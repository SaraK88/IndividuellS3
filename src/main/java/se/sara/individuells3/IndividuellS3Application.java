package se.sara.individuells3;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

@SpringBootApplication
public class IndividuellS3Application implements CommandLineRunner {

    public static void main(String[] args) {

        SpringApplication.run(IndividuellS3Application.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // downloading from  .env
        Dotenv dotenv = Dotenv.load();

        String bucketName = dotenv.get("BUCKET_NAME");
        String accessKey = dotenv.get("ACCESS_KEY");
        String secretKey = dotenv.get("SECRET_KEY");

        System.out.println("BUCKET_NAME: " + bucketName);
        System.out.println("ACCESS_KEY: " + accessKey);
        System.out.println("SECRET_KEY: " + secretKey);

        Scanner scanner = new Scanner(System.in);
        // Creating S3Client
        S3Client s3Client = S3Client.builder()
                .credentialsProvider((AwsCredentialsProvider) () ->
                        AwsBasicCredentials.create(accessKey, secretKey))
                .region(Region.EU_NORTH_1)
                .build();

        DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
                .credentialsProvider(new AwsCredentialsProvider() {
                    @Override
                    public AwsCredentials resolveCredentials() {
                        return AwsBasicCredentials.builder()
                                .accessKeyId(accessKey)
                                .secretAccessKey(secretKey).build();
                    }
                })
                .region(Region.EU_NORTH_1)
                .build();
        //2.
        GetItemRequest getItemRequest = GetItemRequest.builder()
                .tableName("Person")
                .key(Map.of("personnummer", AttributeValue.builder().s("19720803-78331").build()))
                .build();
        GetItemResponse response = dynamoDbClient.getItem(getItemRequest);
        Map<String, AttributeValue> returnedItem = response.item();
        if (returnedItem.isEmpty()) {
            System.out.println("No item found");
        } else {
            String namn = returnedItem.get("namn").s();
            System.out.println("Name: " + namn);
        }
        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName("Person")
                .item(Map.of( "personnummer",    AttributeValue.builder().s("19910809-1010").build(),
                        "namn", AttributeValue.builder().s("Filip").build(),
                        "isCool", AttributeValue.builder().s("YES!").build()) )
                .build();
        dynamoDbClient.putItem( putItemRequest );
        //Dynamo code end ********************************************

        // Menu
        while (true) {
            System.out.println("1. Lista alla filer");
            System.out.println("2. Ladda upp fil");
            System.out.println("3. Ladda ner fil");
            System.out.println("4. Avsluta");
            System.out.print("Choose an option: ");

            int choice = Integer.parseInt(scanner.nextLine());

            switch (choice) {
                case 1:
                    System.out.println("Nu listas alla filer");

                    ListObjectsV2Request listReq = ListObjectsV2Request.builder()
                            .bucket(bucketName)
                            .build();

                    ListObjectsV2Response listRes = s3Client.listObjectsV2(listReq);

                    List<String> fileNames = listRes.contents().stream()
                            .map(S3Object::key)
                            .collect(Collectors.toList());

                    fileNames.forEach(System.out::println);
                    break;

                case 2:
                    System.out.println("Vilken fil vill du ladda upp?");
                    break;

                case 3:
                    System.out.println("Vilken fil vill du ladda ner");
                    break;

                case 4:
                    System.out.println("Avsluta");
                    return;

                default:
                    System.out.println("Fel val, försök igen.");
            }
        }
    }
}
