package com.banamex.pdf.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.PRStream;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfObject;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.ImageRenderInfo;
import com.itextpdf.text.pdf.parser.PdfImageObject;
import com.itextpdf.text.pdf.parser.PdfReaderContentParser;
import com.itextpdf.text.pdf.parser.RenderListener;
import com.itextpdf.text.pdf.parser.TextRenderInfo;
import com.itextpdf.text.pdf.parser.Vector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PDFAllExporter {

    private static final ObjectMapper jsonMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    
    public static class ExtractedSignatureResult {
        private EnrichedSignatureMetadata metadata;
        private byte[] imageBytes;
        private String suggestedFileName;

        public ExtractedSignatureResult(EnrichedSignatureMetadata metadata, byte[] imageBytes, String suggestedFileName) {
            this.metadata = metadata;
            this.imageBytes = imageBytes;
            this.suggestedFileName = suggestedFileName;
        }

        public EnrichedSignatureMetadata getMetadata() { return metadata; }
        public byte[] getImageBytes() { return imageBytes; }
        public String getSuggestedFileName() { return suggestedFileName; }

        public void saveToDirectory(File outputDir) throws IOException {
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            File imgFile = new File(outputDir, suggestedFileName + "." + metadata.imageDimensions.fileType.toLowerCase());
            try (FileOutputStream fos = new FileOutputStream(imgFile)) {
                fos.write(imageBytes);
            }
            metadata.fileName = imgFile.getName();

            File jsonFile = new File(outputDir, suggestedFileName + ".json");
            jsonMapper.writeValue(jsonFile, metadata);

            System.out.println("Guardado exitoso:");
            System.out.println("   - Imagen: " + imgFile.getAbsolutePath());
            System.out.println("   - JSON:   " + jsonFile.getAbsolutePath());
        }
    }

    public static class EnrichedSignatureMetadata {
        public String fileName;
        public String fieldName;
        public String type = "ACROFIELD_SIGNATURE_IMAGE";
        public int pageNumber;
        public ImageDimensions imageDimensions = new ImageDimensions();
        public BoundingBox fieldBoundingBox;
        public SignatureDigitalInfo digitalSignatureInfo = new SignatureDigitalInfo();
        public String nearbyDocumentContext;
        public String pageZone;
    }

    public static class ImageDimensions {
        public int width;
        public int height;
        public String fileType;
        public long sizeInBytes;
    }

    public static class BoundingBox {
        public float llx, lly, urx, ury, width, height;

        public BoundingBox(AcroFields.FieldPosition pos) {
            this.llx = pos.position.getLeft();
            this.lly = pos.position.getBottom();
            this.urx = pos.position.getRight();
            this.ury = pos.position.getTop();
            this.width = pos.position.getWidth();
            this.height = pos.position.getHeight();
        }
    }

    public static class SignatureDigitalInfo {
        public String nameOrReason;
        public String location;
        public String signingDate;
        public String contactInfo;
        public String filter;
        public boolean isCryptoSigned;
    }

    private static class TextChunk {
        String text; 
        float x, y;
        TextChunk(String text, float x, float y) { 
            this.text = text; 
            this.x = x; 
            this.y = y; 
        }
    }

    public static ExtractedSignatureResult extractMatchingSignature(String inputPdfPath, String targetText) {
        if (targetText == null || targetText.trim().isEmpty()) {
            throw new IllegalArgumentException("El texto de búsqueda (targetText) no puede ser nulo o vacío.");
        }

        PdfReader reader = null;
        try {
            reader = new PdfReader(inputPdfPath);
            AcroFields fields = reader.getAcroFields();
            ArrayList<String> signatureNames = fields.getSignatureNames();

            Set<Integer> processedStreamIds = new HashSet<>();
            ExtractedSignatureResult[] resultHolder = new ExtractedSignatureResult[1];

            for (String fieldName : signatureNames) {
                if (resultHolder[0] != null) break;

                List<AcroFields.FieldPosition> positions = fields.getFieldPositions(fieldName);
                if (positions == null || positions.isEmpty()) continue;

                AcroFields.FieldPosition mainPos = positions.get(0);
                int pageNum = mainPos.page;

                List<TextChunk> pageTexts = extractTextChunksFromPage(reader, pageNum);

                PdfDictionary sigDict = fields.getSignatureDictionary(fieldName);
                SignatureDigitalInfo sigInfo = extractDigitalSignatureDetails(sigDict);

                AcroFields.Item item = fields.getFieldItem(fieldName);

                for (int i = 0; i < item.size(); i++) {
                    if (resultHolder[0] != null) break;

                    PdfDictionary widgetDict = item.getWidget(i);
                    if (widgetDict == null) continue;

                    scanAndExtractMatch(widgetDict, fieldName, pageNum, mainPos, 
                            sigInfo, pageTexts, reader, processedStreamIds, targetText, resultHolder);
                }
            }

            return resultHolder[0];

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private static void scanAndExtractMatch(PdfDictionary dict, String fieldName, int pageNum, 
                                            AcroFields.FieldPosition pos, SignatureDigitalInfo sigInfo,
                                            List<TextChunk> pageTexts, PdfReader reader, 
                                            Set<Integer> processedStreams, String targetText,
                                            ExtractedSignatureResult[] resultHolder) throws IOException {
        if (dict == null || resultHolder[0] != null) return;

        for (PdfName key : dict.getKeys()) {
            if (resultHolder[0] != null) break;

            PdfObject obj = dict.getDirectObject(key);

            if (obj instanceof PRStream) {
                PRStream stream = (PRStream) obj;
                PdfName subtype = stream.getAsName(PdfName.SUBTYPE);

                if (PdfName.IMAGE.equals(subtype)) {
                    int streamId = stream.getIndRef() != null ? stream.getIndRef().getNumber() : stream.hashCode();

                    if (!processedStreams.contains(streamId)) {
                        processedStreams.add(streamId);

                        BoundingBox bbox = new BoundingBox(pos);
                        String nearbyContext = findClosestText(bbox, pageTexts);

                        if (nearbyContext != null && nearbyContext.toUpperCase().contains(targetText.toUpperCase())) {
                            
                            try {
                                PdfImageObject imageObj = new PdfImageObject(stream);
                                byte[] bytes = imageObj.getImageAsBytes();

                                if (bytes != null && bytes.length > 0) {
                                    String ext = imageObj.getFileType();
                                    String suggestedFileName = String.format("firma_%s_%s", 
                                            sanitizeFileName(fieldName), sanitizeFileName(targetText));

                                    EnrichedSignatureMetadata meta = new EnrichedSignatureMetadata();
                                    meta.fieldName = fieldName;
                                    meta.pageNumber = pageNum;
                                    
                                    meta.imageDimensions.fileType = ext.toUpperCase();
                                    meta.imageDimensions.sizeInBytes = bytes.length;
                                    PdfObject w = stream.get(PdfName.WIDTH);
                                    PdfObject h = stream.get(PdfName.HEIGHT);
                                    if (w != null) meta.imageDimensions.width = Integer.parseInt(w.toString());
                                    if (h != null) meta.imageDimensions.height = Integer.parseInt(h.toString());

                                    meta.fieldBoundingBox = bbox;
                                    meta.digitalSignatureInfo = sigInfo;
                                    meta.pageZone = determineZone(bbox);
                                    meta.nearbyDocumentContext = nearbyContext;

                                    resultHolder[0] = new ExtractedSignatureResult(meta, bytes, suggestedFileName);
                                }
                            } catch (Exception e) {
                                System.err.println("Error procesando imagen de firma: " + e.getMessage());
                            }
                        }
                    }
                } else if (PdfName.FORM.equals(subtype) || stream.contains(PdfName.RESOURCES)) {
                    scanAndExtractMatch(stream, fieldName, pageNum, pos, sigInfo, pageTexts, reader, processedStreams, targetText, resultHolder);
                }
            } else if (obj instanceof PdfDictionary) {
                scanAndExtractMatch((PdfDictionary) obj, fieldName, pageNum, pos, sigInfo, pageTexts, reader, processedStreams, targetText, resultHolder);
            }
        }
    }

    private static SignatureDigitalInfo extractDigitalSignatureDetails(PdfDictionary sigDict) {
        SignatureDigitalInfo info = new SignatureDigitalInfo();
        if (sigDict == null) {
            info.isCryptoSigned = false;
            return info;
        }

        info.isCryptoSigned = true;
        if (sigDict.get(PdfName.NAME) != null) info.nameOrReason = sigDict.get(PdfName.NAME).toString();
        else if (sigDict.get(PdfName.REASON) != null) info.nameOrReason = sigDict.get(PdfName.REASON).toString();

        if (sigDict.get(PdfName.LOCATION) != null) info.location = sigDict.get(PdfName.LOCATION).toString();
        if (sigDict.get(PdfName.M) != null) info.signingDate = sigDict.get(PdfName.M).toString();
        if (sigDict.get(PdfName.CONTACTINFO) != null) info.contactInfo = sigDict.get(PdfName.CONTACTINFO).toString();
        if (sigDict.get(PdfName.FILTER) != null) info.filter = sigDict.get(PdfName.FILTER).toString().replace("/", "");

        return info;
    }

    private static List<TextChunk> extractTextChunksFromPage(PdfReader reader, int pageNum) throws IOException {
        final List<TextChunk> chunks = new ArrayList<>();
        PdfReaderContentParser parser = new PdfReaderContentParser(reader);

        parser.processContent(pageNum, new RenderListener() {
            @Override
            public void renderText(TextRenderInfo renderInfo) {
                String txt = renderInfo.getText();
                if (txt != null && !txt.trim().isEmpty()) {
                    Vector pos = renderInfo.getBaseline().getStartPoint();
                    chunks.add(new TextChunk(txt.trim(), pos.get(0), pos.get(1)));
                }
            }
            @Override public void renderImage(ImageRenderInfo renderInfo) {}
            @Override public void beginTextBlock() {}
            @Override public void endTextBlock() {}
        });

        return chunks;
    }

    private static String findClosestText(BoundingBox bbox, List<TextChunk> textChunks) {
        String closestText = "No detectado";
        double minDistance = Double.MAX_VALUE;
        float centerX = bbox.llx + (bbox.width / 2);
        float centerY = bbox.lly + (bbox.height / 2);

        for (TextChunk chunk : textChunks) {
            double dist = Math.hypot(chunk.x - centerX, chunk.y - centerY);
            if (dist < minDistance) {
                minDistance = dist;
                closestText = chunk.text;
            }
        }
        return closestText;
    }

    private static String determineZone(BoundingBox bbox) {
        String vertical = bbox.lly > 500 ? "SUPERIOR" : (bbox.lly > 250 ? "CENTRO" : "INFERIOR");
        String horizontal = bbox.llx > 400 ? "DERECHA" : (bbox.llx > 200 ? "CENTRO" : "IZQUIERDA");
        return vertical + "_" + horizontal;
    }

    private static String sanitizeFileName(String name) {
        return name == null ? "firma" : name.replaceAll("[^a-zA-Z0-9_]", "_");
    }


    public static void main(String[] args) {
        String pdfPath = "contrato_no_jalo.pdf";
        String nombreABuscar = "PETER PAKER";
        File carpetaDestino = new File("./exportacion_firma");

        ExtractedSignatureResult result = PDFAllExporter.extractMatchingSignature(pdfPath, nombreABuscar);

        if (result != null) {
            System.out.println("Coincidencia encontrada!");
            System.out.println("Contexto detectado: " + result.getMetadata().nearbyDocumentContext);
            System.out.println("Bytes de imagen obtenidos: " + result.getImageBytes().length + " bytes");

            try {
                result.saveToDirectory(carpetaDestino);
            } catch (IOException e) {
                System.err.println("Error guardando archivos en carpeta: " + e.getMessage());
            }
        } else {
            System.out.println("No se encontró ninguna firma coincidente para: '" + nombreABuscar + "'");
        }
    }
}