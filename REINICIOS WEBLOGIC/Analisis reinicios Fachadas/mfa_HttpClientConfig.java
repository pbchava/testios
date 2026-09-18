package com.citibanamex.config;

import com.citibanamex.rest.model.dto.Parametros;
import com.citibanamex.rest.service.SessionParamsProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.pool.PoolStats;
import org.apache.http.protocol.HTTP;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.ws.transport.http.HttpComponentsMessageSender;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * - Supports both HTTP and HTTPS - Uses a connection pool to re-use connections
 * and save overhead of creating connections. - Has a custom connection
 * keep-alive strategy (to apply a default keep-alive if one isn't specified) -
 * Starts an idle connection monitor to continuously clean up stale connections.
 *
 * AJUSTES respecto a la versión anterior (ver explicación al final del archivo /
 * en el mensaje que acompaña esta entrega):
 *  1) Se retiraron .evictIdleConnections() / .evictExpiredConnections() del builder
 *     de CloseableHttpClient: ese mecanismo nativo de Apache HttpClient crea un
 *     thread daemon propio ("Connection evictor") por cada CloseableHttpClient
 *     construido, y solo se detiene si se invoca close() sobre ESE cliente en
 *     particular. Al convivir con el monitor @Scheduled ya existente, cualquier
 *     reconstrucción del bean (redeploy parcial, refresh de contexto, etc.) deja
 *     ese thread huérfano para siempre. Ahora solo hay UN mecanismo de limpieza:
 *     el connectionPoolMonitor @Scheduled.
 *  2) connectionManager() ya no puede devolver null: si falla la construcción del
 *     SSLContext, se propaga una excepción y falla el arranque en vez de dejar un
 *     bean a medio construir que provoque NullPointerException aguas abajo.
 *  3) La clase implementa DisposableBean y cierra explícitamente el
 *     CloseableHttpClient y el PoolingHttpClientConnectionManager al destruir el
 *     contexto, para que el shutdown (incluyendo un redeploy) libere sockets y
 *     conexiones de forma determinística.
 */
@Slf4j
@Configuration
@EnableScheduling
public class HttpClientConfig implements DisposableBean {

    private final SessionParamsProvider paramsProvider;

    private static final int DEFAULT_KEEP_ALIVE_TIME_MILLIS = 20 * 1000;
    private static final int CLOSE_IDLE_CONNECTION_WAIT_TIME_SECS = 30;

    private PoolingHttpClientConnectionManager connectionManagerRef;
    private CloseableHttpClient closeableHttpClientRef;

    public HttpClientConfig(SessionParamsProvider paramsProvider) {
        this.paramsProvider = paramsProvider;
    }

    @Bean
    public PoolingHttpClientConnectionManager connectionManager() {
        Parametros parametros = paramsProvider.getCacheParameters();
        try {
            SSLContextBuilder builder = new SSLContextBuilder();
            builder.loadTrustMaterial(null, (certificate, authtype) -> true);
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build(),
                    (hostname, session) -> true);

            Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("https", sslsf)
                    .register("http", new PlainConnectionSocketFactory()).build();
            PoolingHttpClientConnectionManager poolingConnectionManager =
                    new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            poolingConnectionManager.setMaxTotal(parametros.getMaxTotal());
            poolingConnectionManager.setDefaultMaxPerRoute(20);
            poolingConnectionManager.setValidateAfterInactivity(5000);
            this.connectionManagerRef = poolingConnectionManager;
            return poolingConnectionManager;
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            // AJUSTE: antes NoSuchAlgorithmException/KeyManagementException solo se
            // logueaban y el método devolvía null (bean inválido). Ahora se falla
            // rápido y explícito para no arrancar con un pool de conexiones inexistente.
            log.error("Pooling Connection Manager Initialisation failure because of {} ", e.getMessage(), e);
            throw new IllegalStateException("No se pudo inicializar PoolingHttpClientConnectionManager", e);
        }
    }

    /**
     * ayuda a establecer el tiempo que decide cuánto tiempo puede permanecer inactiva una conexión antes de ser reutilizada.
     *
     * @return ConnectionKeepAliveStrategy
     */
    @Bean
    public ConnectionKeepAliveStrategy keepAliveStrategy() {
        return (response, context) -> {
            HeaderElementIterator it = new BasicHeaderElementIterator(
                    response.headerIterator(HTTP.CONN_KEEP_ALIVE));
            while (it.hasNext()) {
                HeaderElement he = it.nextElement();
                String param = he.getName();
                String value = he.getValue();

                if (value != null && param.equalsIgnoreCase("timeout")) {
                    return Long.parseLong(value) * 1000;
                }
            }
            return DEFAULT_KEEP_ALIVE_TIME_MILLIS;
        };
    }

    @Bean
    public RequestConfig requestConfig() {
        Parametros configurationsDto = paramsProvider.getCacheParameters();
        return RequestConfig.custom()
                .setConnectTimeout(configurationsDto.getConnectTimeout())
                .setConnectionRequestTimeout(configurationsDto.getConnectionRequestTimeout())
                .setSocketTimeout(configurationsDto.getSocketTimeout()).build();
    }

    @Bean
    public CloseableHttpClient closeableHttpClient(PoolingHttpClientConnectionManager connectionManager,
                                                   RequestConfig requestConfig,
                                                   ConnectionKeepAliveStrategy keepAliveStrategy) {
        // AJUSTE: se eliminaron .evictIdleConnections(...) y .evictExpiredConnections()
        // de este builder. La limpieza de conexiones inactivas/expiradas queda a cargo
        // ÚNICAMENTE del bean connectionPoolMonitor (@Scheduled más abajo), que opera
        // sobre este mismo connectionManager. Con esto se evita tener dos hilos
        // independientes haciendo el mismo trabajo y, sobre todo, se evita el riesgo
        // de que un evictor nativo quede huérfano si este bean llegara a reconstruirse.
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(connectionManager)
                .setKeepAliveStrategy(keepAliveStrategy)
                .addInterceptorFirst(new HttpComponentsMessageSender.RemoveSoapHeadersInterceptor())
                .build();
        this.closeableHttpClientRef = httpClient;
        return httpClient;
    }

    @Bean
    public Runnable connectionPoolMonitor(PoolingHttpClientConnectionManager poolingHttpClientConnectionManager) {
        return new Runnable() {

            @Scheduled(fixedDelay = 10000)
            public void run() {
                try {

                    if (poolingHttpClientConnectionManager != null) {
                        log.trace("run IdleConnectionMonitor - Closing expired and idle connections...");
                        poolingHttpClientConnectionManager.closeExpiredConnections();// Closes all expired connections in the pool.
                        poolingHttpClientConnectionManager.closeIdleConnections(CLOSE_IDLE_CONNECTION_WAIT_TIME_SECS, TimeUnit.SECONDS);
                        log.trace("*** ConnectionManager TotalStats {} ", poolingHttpClientConnectionManager.getTotalStats());
                    } else {
                        log.trace("run IdleConnectionMonitor - Http Client Connection manager is not initialised");
                    }
                    PoolStats totalStats = poolingHttpClientConnectionManager.getTotalStats();

                    int leased = totalStats.getLeased();
                    int available = totalStats.getAvailable();
                    int pending = totalStats.getPending();
                    int max = totalStats.getMax();

                    log.debug("MFA HTTP POOL → leased: {}, available: {}, pending: {}, max: {}",
                            leased, available, pending, max);

                    // 🚨 ALERTAS
                    if (pending > 0) {
                        log.warn("⚠️ POOL SATURADO: hay hilos esperando conexión");
                    }

                    if (leased >= max * 0.8) {
                        log.warn("⚠️ POOL AL 80% de uso");
                    }

                } catch (Exception e) {
                    log.error("Error monitoreando pool", e);
                }
            }
        };
    }

    /**
     * @return TaskScheduler
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("mfa-pool-");
        scheduler.setPoolSize(1);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }

    /**
     * AJUSTE (nuevo): cierre determinístico de recursos al destruir el contexto
     * (undeploy / redeploy / shutdown del managed server). Esto complementa el
     * cierre automático que ya hace HttpComponentsMessageSender.destroy(), pero
     * lo hace explícito y a prueba de futuros cambios en el wiring de beans:
     * cierra primero el CloseableHttpClient (lo que internamente libera también
     * el PoolingHttpClientConnectionManager que administra) y por seguridad
     * cierra también la referencia directa al connection manager.
     */
    @Override
    public void destroy() {
        try {
            if (closeableHttpClientRef != null) {
                closeableHttpClientRef.close();
                log.info("CloseableHttpClient cerrado correctamente en shutdown de MFA");
            }
        } catch (IOException e) {
            log.warn("No se pudo cerrar CloseableHttpClient en shutdown de MFA: {}", e.getMessage());
        } finally {
            if (connectionManagerRef != null) {
                connectionManagerRef.close();
            }
        }
    }
}