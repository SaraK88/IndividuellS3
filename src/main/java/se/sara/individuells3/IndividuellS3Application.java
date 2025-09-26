package se.sara.individuells3;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@SpringBootApplication
public class IndividuellS3Application implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(IndividuellS3Application.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // Load from .env
        Dotenv dotenv = Dotenv.load();
        String bucketName = dotenv.get("BUCKET_NAME");
        String bucketName1 = dotenv.get("BUCKET_NAME1");
        String accessKey = dotenv.get("ACCESS_KEY");
        String secretKey = dotenv.get("SECRET_KEY");

        Scanner scanner = new Scanner(System.in);

        S3Client s3Client = S3Client.builder()
                .credentialsProvider(() -> AwsBasicCredentials.builder()
                        .accessKeyId(accessKey)
                        .secretAccessKey(secretKey)
                        .build())
                .region(Region.EU_NORTH_1)
                .build();

        while (true) {
            System.out.println("Welcome to the S3 Bucket Manager\n");
            System.out.println("Which bucket do you want to use?");
            System.out.println("1. " + bucketName);
            System.out.println("2. " + bucketName1);
            System.out.println("3. Exit\n");

            String bucketChoice = scanner.nextLine();

            if (bucketChoice.equals("1")) {
                bucketChoice = bucketName;
            } else if (bucketChoice.equals("2")) {
                bucketChoice = bucketName1;
            } else if (bucketChoice.equals("3")) {
                System.out.println("Exit");
            } else {
                System.out.println("Invalid choice, program is shutting down");
                break;
            }

            // Menu
            while (bucketChoice.equals("1") || bucketChoice.equals("2") || !bucketChoice.isEmpty()) {
                System.out.println("\n1. List all files");
                System.out.println("2. Upload file");
                System.out.println("3. Download file");
                System.out.println("4. Search files");
                System.out.println("5. Delete file");
                System.out.println("6. Upload folder as zip");
                System.out.println("7. Back to bucket selection");
                System.out.println("Choose an option:\n");

                String choice = scanner.nextLine();
                if (!choice.matches("[1-7]")) {
                    System.out.println("Invalid choice, try again.\n-------\n");
                    continue;
                }

                int intChoice = Integer.parseInt(choice);

                switch (intChoice) {
                    case 1 -> {
                        System.out.println("Listing all files...");
                        ListObjectsV2Response listRes = s3Client.listObjectsV2(
                                ListObjectsV2Request.builder().bucket(bucketChoice).build());
                        List<String> fileNames = listRes.contents().stream()
                                .map(S3Object::key)
                                .collect(Collectors.toList());
                        fileNames.forEach(System.out::println);
                    }
                    case 2 -> {
                        System.out.println("Which file do you want to upload?");
                        Path pathFile = Paths.get(scanner.nextLine());

                        if (!pathFile.toFile().exists()) {
                            System.out.println("The file you selected does not exist, returning to menu.");
                            break;
                        }
                        System.out.println("What should the file be called in the bucket?");
                        String keyToUpload = scanner.nextLine().trim().toLowerCase();
                        PutObjectRequest putReq = PutObjectRequest.builder()
                                .bucket(bucketChoice)
                                .key(keyToUpload).build();
                        s3Client.putObject(putReq, RequestBody.fromFile(pathFile.toFile()));
                        System.out.println("Your file has been uploaded to " + bucketChoice + "/" + keyToUpload);
                    }
                    case 3 -> {
                        List<String> files = listKeys(s3Client, bucketChoice);
                        files.forEach(System.out::println);
                        System.out.println("\nWhich file do you want to download?\n-------\n");
                        String downloadFile = scanner.nextLine().trim();
                        if (!files.contains(downloadFile)) {
                            System.out.println("No match for that filename.");
                            break;
                        }
                        System.out.println("Where do you want to save the file?");
                        Path downloadPath = Paths.get(scanner.nextLine());
                        if (!Files.isDirectory(downloadPath)) {
                            System.out.println("That folder does not exist.");
                            break;
                        }
                        Path fullPath = downloadPath.resolve(Paths.get(downloadFile).getFileName());
                        if (Files.exists(fullPath)) {
                            System.out.println("A file already exists at this location.");
                            break;
                        }
                        GetObjectRequest getReq = GetObjectRequest.builder()
                                .bucket(bucketChoice)
                                .key(downloadFile).build();
                        s3Client.getObject(getReq, ResponseTransformer.toFile(fullPath));
                        System.out.println("File downloaded to: " + fullPath);
                    }
                    case 4 -> {
                        System.out.println("Which file are you searching for?");
                        String searchFile = scanner.nextLine().trim().toLowerCase();
                        List<String> files = listKeys(s3Client, bucketChoice);
                        List<String> matches = files.stream()
                                .filter(f -> f.toLowerCase().contains(searchFile))
                                .toList();
                        if (matches.isEmpty()) {
                            System.out.println(searchFile + " was not found in the bucket.\n-------\n");
                        } else {
                            System.out.println("Files matching your search:\n-------");
                            matches.forEach(System.out::println);
                        }
                    }
                    case 5 -> {
                        System.out.println("Here is a list of files.\n-------\n");
                        List<String> files = listKeys(s3Client, bucketChoice);
                        files.forEach(System.out::println);
                        System.out.println("Which file do you want to delete?\n-------\n");
                        String deleteChoice = scanner.nextLine().trim();
                        if (!files.contains(deleteChoice)) {
                            System.out.println("File not found.");
                            break;
                        }
                        s3Client.deleteObject(DeleteObjectRequest.builder()
                                .bucket(bucketChoice)
                                .key(deleteChoice).build());
                        System.out.println("File " + deleteChoice + " has been deleted.");
                    }
                    case 6 -> {
                        System.out.println("Which folder do you want to upload?");
                        Path folderPath = Paths.get(scanner.nextLine().trim());

                        //Kollar om mappen finns
                        System.out.println("DEBUG: folderPath = '" + folderPath + "'");
                        System.out.println("DEBUG: exists? " + Files.exists(folderPath));
                        System.out.println("DEBUG: isDirectory? " + Files.isDirectory(folderPath));

                        if (!Files.isDirectory(folderPath)) {
                            System.out.println("The folder does not exist, returning to menu.\n");
                            break;
                        }
                        System.out.println("What should the zip file be named?");
                        String zipName = scanner.nextLine().trim().toLowerCase();
                        if (!zipName.endsWith(".zip")) zipName += ".zip";
                        Path tempZip = Files.createTempFile("upload-", ".zip");
                        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempZip))) {
                            Files.walk(folderPath).filter(Files::isRegularFile).forEach(p -> {
                                String entry = folderPath.relativize(p).toString().replace('\\','/');
                                try {
                                    zos.putNextEntry(new ZipEntry(entry));
                                    Files.copy(p, zos);
                                    zos.closeEntry();
                                } catch (IOException ex) { throw new RuntimeException(ex); }
                            });
                        }
                        PutObjectRequest putReqZip = PutObjectRequest.builder()
                                .bucket(bucketChoice)
                                .key(zipName)
                                .contentType("application/zip")
                                .build();
                        s3Client.putObject(putReqZip, RequestBody.fromFile(tempZip));
                        Files.deleteIfExists(tempZip);
                        System.out.println("Folder uploaded as " + zipName);
                    }
                    case 7 -> System.out.println("Going back to bucket selection");
                }
                if (intChoice == 7) break;
            }
        }
    }
    //Jag gjorde en utility metod för att lista filer i en bucket, för att undvika kodupprepning
    private static List<String> listKeys(S3Client s3, String bucket) {
        ListObjectsV2Response listRes = s3.listObjectsV2(
                ListObjectsV2Request.builder().bucket(bucket).build());
        return listRes.contents().stream()
                .map(S3Object::key)
                .filter(k -> !k.endsWith("/"))
                .collect(Collectors.toList());
    }
}
