package org.example;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.commons.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static Tesseract tesseract = null;
    private static String tessDataDir = null;
    private static int filesProcessed = 0;
    private static int filesRenamed = 0;

    private static BufferedImage getImageFromPDFFile(String pdfFileName) {
        try (PDDocument document = PDDocument.load(new File(pdfFileName))) {
            System.out.println("Converting page 1 to an image ...");
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            return pdfRenderer.renderImageWithDPI(0, 300, ImageType.RGB);
        } catch (IOException e) {
            System.out.println("Error when converting `" + pdfFileName + "` to an image: " + e);
        }

        return null;
    }

    private static String getTextFromImage(BufferedImage bufferedImage) throws TesseractException {
        if (tesseract == null) {
            tesseract = new Tesseract();
            tesseract.setDatapath(tessDataDir);
        }

        System.out.println("Converting the image to text via OCR ...");
        return tesseract.doOCR(bufferedImage);
    }

    private static void extractResource(Path outputDirectory) throws IOException {
        try (InputStream in = Main.class.getResourceAsStream("/tessdata/eng.traineddata")) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + "/tessdata/eng.traineddata");
            }
            String fileName = Paths.get("/tessdata/eng.traineddata").getFileName().toString();
            Path outputPath = outputDirectory.resolve(fileName);
            try (OutputStream out = Files.newOutputStream(outputPath)) {
                IOUtils.copy(in, out);
            }
        }
    }

    private static void renamePDFFile(String fileName) {
        filesProcessed += 1;
        System.out.println("----------------------------------");
        System.out.println("Analyzing file: `" + fileName + "` ...");
        BufferedImage bufferedImage = getImageFromPDFFile(fileName);
        if (bufferedImage != null) {
            try {
                String result = getTextFromImage(bufferedImage);
                int batchNumberIndexStart = result.indexOf("Batch number:");
                if (batchNumberIndexStart > 0) {
                    int batchNumberIndexEnd = result.indexOf("\n", batchNumberIndexStart);
                    if (batchNumberIndexEnd > batchNumberIndexStart) {
                        String batchNumber = result.substring(batchNumberIndexStart + 13, batchNumberIndexEnd).trim();
                        if (!batchNumber.isEmpty()) {
                            System.out.println("Found `Batch number:" + batchNumber + "`");

                            Path path = Paths.get(fileName);
                            String parentPath = path.getParent().toString();
                            String fileNamePath = path.getFileName().toString();

                            if (!fileNamePath.equalsIgnoreCase(batchNumber+".pdf")) {
                                Path target = Paths.get(parentPath + "\\" + batchNumber + ".pdf");
                                if (!Files.exists(target)) {
                                    System.out.println("+++++ Renaming file `" + fileName + "` as `" + target+"` +++++");
                                    Files.move(path, target);

                                    filesRenamed += 1;
                                } else {
                                    System.out.println("***** A file with the same batch number already exists *****");
                                }
                            } else {
                                System.out.println("***** File was already renamed *****");
                            }
                            return;
                        }
                    }
                }

                System.out.println("***** Skipping file (Does not contain a batch number) *****");
            } catch (TesseractException | IOException e) {
                System.out.println("Error when converting image to text via OCR: " + e);
            }
        }
    }


    public static void main(String[] args) throws IOException {
        boolean folderParameterExists = false;
        if (args.length >= 2 && ("-f".equalsIgnoreCase(args[0]) || "--folder".equalsIgnoreCase(args[0]))) {
            folderParameterExists = true;
            Path folderPath = Paths.get(args[1]);

            String tempDirectoryPath = System.getProperty("java.io.tmpdir");
            tessDataDir = tempDirectoryPath + File.separator + "renamepdftobatchnumber";
            Path tessdataPath = Paths.get(tessDataDir);

            // Check if directory exists, create if it doesn't
            if (!Files.exists(tessdataPath) || !Files.exists(Paths.get(tessdataPath + "/eng.traineddata"))) {
                Files.createDirectories(tessdataPath);
                // Extract the tessdata resources to the directory
                extractResource(tessdataPath);
            }

            try (Stream<Path> paths = Files.list(folderPath)) { //Replace Files.list with Files.walk to search in subdirectories as well.
                paths
                        // Filter files only (exclude subdirectories)
                        .filter(Files::isRegularFile)
                        // Filter .pdf files
                        .filter(path -> path.toString().endsWith(".pdf"))
                        // Call renamePDFFile for each file's path
                        .forEach(path -> renamePDFFile(path.toString()));
            } catch (IOException e) {
                System.out.println("Error when scanning for PDF files in folder `" + folderPath + "`: " + e);
            }
        }

        if (filesProcessed > 0) {
            System.out.println("----------------------------------");
            System.out.printf("%d file(s) renamed of %d found.%n", filesRenamed, filesProcessed);
            System.out.println("----------------------------------");
        }

        if (!folderParameterExists) {
            System.out.println("Missing required parameters (-f or --folder) \"pathToFolder\"");
        }
    }
}