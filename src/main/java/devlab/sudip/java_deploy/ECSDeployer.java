package devlab.sudip.java_deploy;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.AmazonECSClientBuilder;
import com.amazonaws.services.ecs.model.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECSDeployer {

    private static final Logger logger = LoggerFactory.getLogger(ECSDeployer.class);

    public static void main(String[] args) {
        // Hardcoded subnets
        String subnetCsv = "subnet-09ef764a2d1106d7d,subnet-0e43ae9ec9b7737ce";
        String[] subnets = subnetCsv.split(",");

        for (String subnet : subnets) {
            System.out.println("Deploying to subnet: " + subnet);
        }

        // ---------------- Configuration ----------------
        String clusterName = System.getenv("ECS_CLUSTER");
        String serviceName = System.getenv("ECS_SERVICE");
        String image = System.getenv("DOCKER_IMAGE");
        String awsRegion = System.getenv("AWS_REGION");
         if (clusterName == null || serviceName == null || image == null || awsRegion == null) {
            throw new RuntimeException("One or more required environment variables are missing");
        }

        String sgCsv = System.getenv("SECURITY_GROUPS");
        if (sgCsv == null || sgCsv.isBlank()) {
            throw new RuntimeException("SECURITY_GROUPS env variable is not set");
        }
        String[] securityGroups = sgCsv.split(",");

        
        //? ---------------- Initialize ECS client ----------------
        AmazonECS ecs = AmazonECSClientBuilder.standard().withRegion(awsRegion).build();

        try {
            //? ---------------- 1️⃣ Register Task Definition ----------------
            ContainerDefinition container = new ContainerDefinition()
                    .withName("app")
                    .withImage(image)
                    .withCpu(256)
                    .withMemory(512)
                    .withPortMappings(new PortMapping().withContainerPort(8080))
                    .withEnvironment(
                            new KeyValuePair().withName("ENV").withValue("prod"),
                            new KeyValuePair().withName("SERVER_PORT").withValue("8080")
                    )
                    .withLogConfiguration(new LogConfiguration()
                            .withLogDriver(LogDriver.Awslogs)
                            .withOptions(Map.of(
                                    "awslogs-group", "/ecs/java-docker-app",
                                    "awslogs-region", awsRegion,
                                    "awslogs-stream-prefix", "ecs"
                            ))
                    );

            RegisterTaskDefinitionRequest taskDefRequest = new RegisterTaskDefinitionRequest()
                    .withFamily("java-docker-app")
                    .withNetworkMode(NetworkMode.Awsvpc)
                    .withRequiresCompatibilities(Compatibility.FARGATE)
                    .withCpu("256")
                    .withMemory("512")
                    .withContainerDefinitions(container)
                    .withExecutionRoleArn("arn:aws:iam::450372565696:role/ECSJavaAppExecutionRole");

            RegisterTaskDefinitionResult taskDefResult = ecs.registerTaskDefinition(taskDefRequest);
            String taskDefArn = taskDefResult.getTaskDefinition().getTaskDefinitionArn();
            logger.info("Task definition registered: {}", taskDefArn);

            // ---------------- 2️⃣ Check if ECS Service exists ----------------
            DescribeServicesRequest describeRequest = new DescribeServicesRequest().withCluster(clusterName).withServices(serviceName);

            DescribeServicesResult describeResult = ecs.describeServices(describeRequest);
            List<Service> services = describeResult.getServices();
            Service firstService = services.isEmpty() ? null : services.get(0);

            if (firstService == null  || firstService.getStatus().equals("INACTIVE")) {
                // ---------------- 3️⃣ Create ECS Service ----------------
                CreateServiceRequest createRequest = new CreateServiceRequest()
                        .withCluster(clusterName)
                        .withServiceName(serviceName)
                        .withTaskDefinition(taskDefArn)
                        .withDesiredCount(1)
                        .withLaunchType(LaunchType.FARGATE)
                        .withNetworkConfiguration(new NetworkConfiguration()
                                .withAwsvpcConfiguration(new AwsVpcConfiguration()
                                        .withSubnets(subnets)
                                        .withSecurityGroups(securityGroups)
                                        .withAssignPublicIp("ENABLED")
                                )
                        );

                ecs.createService(createRequest);
                logger.info("ECS Service created: {}", serviceName);

            } else {
                // ---------------- 4️⃣ Update ECS Service ----------------
                UpdateServiceRequest updateService = new UpdateServiceRequest()
                        .withCluster(clusterName)
                        .withService(serviceName)
                        .withTaskDefinition(taskDefArn)
                        .withForceNewDeployment(true);

                ecs.updateService(updateService);
                logger.info("ECS Service updated: {}", serviceName);
            }

            //? ---------------- 5️⃣ Wait for ECS task to get Public IP ----------------
            logger.info("Fetching ECS task public IP...");
            // ---------------- 3️⃣ WAIT FOR RUNNING TASK ----------------
            logger.info("Waiting for running ECS task...");
            String taskArn = waitForRunningTask(ecs, clusterName, serviceName);

            // ---------------- 4️⃣ WAIT FOR PUBLIC IP ----------------
            logger.info("Waiting for public IP...");
            String publicIp = waitForPublicIp(ecs, clusterName, taskArn);


            String healthUrl = "http://" + publicIp + ":8080/actuator/health";
            logger.info("Health URL: {}", healthUrl);

            // ---------------- 5️⃣ HEALTH CHECK ----------------
            waitForHealthyContainer(healthUrl);

            logger.info("✅ Deployment completed successfully!");// 5 min timeout, 10 sec interval

        } catch (Exception e) {
            logger.error("Deployment failed: {}", String.valueOf(e));
        }
    }

    private static String waitForRunningTask(AmazonECS ecs, String cluster, String service)
            throws InterruptedException {

        for (int i = 0; i < 30; i++) {
            List<String> tasks = ecs.listTasks(new ListTasksRequest()
                    .withCluster(cluster)
                    .withServiceName(service)
                    .withDesiredStatus(DesiredStatus.RUNNING))
                    .getTaskArns();

            if (!tasks.isEmpty()) {
                return tasks.get(0);
            }

            Thread.sleep(10_000);
        }
        throw new RuntimeException("No running ECS task found");
    }

    private static String waitForPublicIp(AmazonECS ecs, String cluster, String taskArn)
            throws InterruptedException {

        for (int i = 0; i < 30; i++) {
            DescribeTasksResult result = ecs.describeTasks(
                    new DescribeTasksRequest()
                            .withCluster(cluster)
                            .withTasks(taskArn));

            Task task = result.getTasks().get(0);

            for (Attachment att : task.getAttachments()) {
                for (KeyValuePair kv : att.getDetails()) {
                    if ("publicIPv4Address".equals(kv.getName())) {
                        return kv.getValue();
                    }
                }
            }
            Thread.sleep(10_000);
        }
        throw new RuntimeException("Public IP not assigned");
    }


    private static void waitForHealthyContainer(String url)
            throws InterruptedException {

        HttpClient client = HttpClient.newHttpClient();

        for (int i = 0; i < 30; i++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

                HttpResponse<Void> response =
                        client.send(request, HttpResponse.BodyHandlers.discarding());

                if (response.statusCode() == 200) {
                    logger.info("✅ Container is healthy");
                    return;
                }
            } catch (Exception ignored) {
            }
            Thread.sleep(10_000);
        }
        throw new RuntimeException("Health check failed");
    }
}
