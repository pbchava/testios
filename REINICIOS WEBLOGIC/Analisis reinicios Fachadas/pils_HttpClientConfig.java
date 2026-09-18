package com.citibanamex.pils.configuration;

import com.citibanamex.pils.dao.ClientParams;
import com.citibanamex.pils.generate.add.ClientDetails;
import com.citibanamex.pils.service.SessionParamsProvider;
import com.citibanamex.pils.utils.UtilsRequest;
import com.citibanamex.pils.ws.client.KeepAliveStrategy;
import com.citibanamex.pils.ws.utils.SslUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.pool.PoolStats;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.ws.transport.http.HttpComponentsMessageSender;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

/**
 * - Supports both HTTP and HTTPS - Uses a connection pool to re-use connections
 * and save overhead of creating connections. - Has a custom connection
 * keep-alive strategy (to apply a default keep-alive if one isn't specified) -
 * Starts an idle connection monitor to continuously clean up stale connections. //
 *
 * AJUSTE respecto a la versión anterior: esta clase ya NO usaba
 * .evictIdleConnections()/.evictExpiredConnections() del builder, así que no
 * tenía el problema de threads "Connection evictor" duplicados detectado en
 * mfa. El único cambio aquí es agregar un cierre determinístico de recursos
 * (DisposableBean) para que, ante un undeploy/redeploy del WAR en WebLogic,
 * el CloseableHttpClient y su PoolingHttpClientConnectionManager se cierren
 * de forma explícita en vez de depender únicamente del ciclo de vida por
 * defecto de HttpComponentsMessageSender.
 */
@Configuration
@EnableScheduling
@Slf4j
public class HttpClientConfig implements DisposableBean {

    static final int CLOSE_IDLE_CONNECTION_WAIT_TIME_SECS = 30;

    private final SessionParamsProvider paramsProvider;

    private PoolingHttpClientConnectionManager connectionManagerRef;
    private CloseableHttpClient closeableHttpClientRef;

    public HttpClientConfig(SessionParamsProvider paramsProvider) {
        this.paramsProvider = paramsProvider;
    }

    @Bean
    public PoolingHttpClientConnectionManager poolingHttpClientConnectionManager(SSLConnectionSocketFactory sslConnectionSocketFactory) {
        ClientParams clientParams = paramsProvider.getCacheParameters();
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("https", sslConnectionSocketFactory).register("http", new PlainConnectionSocketFactory()).build();
        PoolingHttpClientConnectionManager poolingConnectionManager =
                new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        poolingConnectionManager.setMaxTotal(clientParams.getMaxTotal());
        poolingConnectionManager.setDefaultMaxPerRoute(20);
        poolingConnectionManager.setValidateAfterInactivity(5000);
        this.connectionManagerRef = poolingConnectionManager;
        return poolingConnectionManager;
    }

    @Bean
    protected SSLConnectionSocketFactory sslConnectionSocketFactory() {
        ClientParams clientParams = paramsProvider.getCacheParameters();
        try {
            return new SSLConnectionSocketFactory(SslUtil.getSslSocketFactory(
                    clientParams.getClientKeyStore().getKeyFile(),
                    clientParams.getClientKeyStore().getKeySecret(),
                    clientParams.getClientKeyStore().getKeyType())
                    , NoopHostnameVerifier.INSTANCE);
        } catch (Exception e) {
            log.info(e.getMessage(), e);
            return new SSLConnectionSocketFactory(SslUtil.getSslSocketFactory(
                    clientParams.getClientKeyStore().getKeyFile(),
                    clientParams.getClientKeyStore().getKeySecret(),
                    clientParams.getClientKeyStore().getKeyType())
                    , NoopHostnameVerifier.INSTANCE);
        }
    }


    @Bean
    public RequestConfig requestConfig() throws NumberFormatException {
        ClientParams clientParams = paramsProvider.getCacheParameters();
        return RequestConfig.custom()
                .setConnectTimeout(clientParams.getConnectTimeout())
                .setConnectionRequestTimeout(clientParams.getConnectionRequestTimeout())
                .setSocketTimeout(clientParams.getSocketTimeout()).build();
    }

    /**
     * ayuda a establecer el tiempo que decide cuánto tiempo puede permanecer
     * inactiva una conexión antes de ser reutilizada.
     *
     * @return ConnectionKeepAliveStrategy
     */
    @Bean
    public ConnectionKeepAliveStrategy connectionKeepAliveStrategy() {
        return new KeepAliveStrategy();
    }

    @Bean
    public CloseableHttpClient closeableHttpClient(RequestConfig requestConfig,
                                                   ConnectionKeepAliveStrategy connectionKeepAliveStrategy,
                                                   PoolingHttpClientConnectionManager poolingHttpClientConnectionManager) throws NumberFormatException {
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .addInterceptorFirst(new HttpComponentsMessageSender.RemoveSoapHeadersInterceptor())
                .setConnectionManager(poolingHttpClientConnectionManager)
                .setKeepAliveStrategy(connectionKeepAliveStrategy)
                .build();
        this.closeableHttpClientRef = httpClient;
        return httpClient;
    }

    /**
     * ConnectionKeepAliveStrategy ayuda a establecer el tiempo que decide cuánto
     * tiempo puede permanecer inactiva una conexión antes de ser reutilizada.
     *
     * @return TaskScheduler
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("pils-pool-");
        scheduler.setPoolSize(1);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }

    /**
     * idleConnectionMonitor periódicamente verifica todas las conexiones y libera
     * las que no se han utilizado y ha transcurrido el tiempo de inactividad.
     *
     * @param
     * @return Runnable
     */
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
                        log.trace("*** connectionManager.getTotalStats {} ", poolingHttpClientConnectionManager.getTotalStats());
                    } else {
                        log.trace("run IdleConnectionMonitor - Http Client Connection manager is not initialised");
                    }

                    PoolStats totalStats = poolingHttpClientConnectionManager.getTotalStats();

                    int leased = totalStats.getLeased();
                    int available = totalStats.getAvailable();
                    int pending = totalStats.getPending();
                    int max = totalStats.getMax();

                    log.debug("AO PILS HTTP POOL → leased: {}, available: {}, pending: {}, max: {}",
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
     * AJUSTE (nuevo): cierre determinístico de recursos al destruir el contexto
     * (undeploy / redeploy / shutdown del managed server). Complementa el cierre
     * automático de HttpComponentsMessageSender.destroy(), haciéndolo explícito
     * y a prueba de futuros cambios en el wiring de beans.
     */
    @Override
    public void destroy() {
        try {
            if (closeableHttpClientRef != null) {
                closeableHttpClientRef.close();
                log.info("CloseableHttpClient cerrado correctamente en shutdown de PILS");
            }
        } catch (IOException e) {
            log.warn("No se pudo cerrar CloseableHttpClient en shutdown de PILS: {}", e.getMessage());
        } finally {
            if (connectionManagerRef != null) {
                connectionManagerRef.close();
            }
        }
    }
}
