//package devlab.sudip.java_deploy;
//import com.amazonaws.services.ecs.AmazonECS;
//import com.amazonaws.services.ecs.AmazonECSClientBuilder;
//import com.amazonaws.services.ecs.model.LogDriver;
//import com.amazonaws.services.ecs.model.*;
//
//import java.net.URI;
//import java.net.http.HttpClient;
//import java.net.http.HttpRequest;
//import java.net.http.HttpResponse;
//import java.util.List;
//import java.util.Map;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//public class ECSDeployer {
//    private static final Logger logger = LoggerFactory.getLogger(ECSDeployer.class);
//
//    public static void main(String[] args) {
//        // Hardcoded subnets
//        String subnetCsv = "subnet-09ef764a2d1106d7d,subnet-0e43ae9ec9b7737ce";
//
//        String[] subnets = subnetCsv.split(",");
//        for (String subnet : subnets) {
//            System.out.println("Deploying to subnet: " + subnet);
//        }
//        // ---------------- Configuration ----------------
//        String clusterName = System.getenv("ECS_CLUSTER");
//        String serviceName = System.getenv("ECS_SERVICE");
//        String image = System.getenv("DOCKER_IMAGE"); // ECR image:tag
//        String awsRegion = System.getenv("AWS_REGION");
////        String subnetCsv = System.getenv("SUBNETS");       // comma-separated
//        String sgCsv = System.getenv("SECURITY_GROUPS");  // comma-separated
//
//        if (clusterName == null || serviceName == null || image == null || awsRegion == null) {
//            throw new RuntimeException("One or more required environment variables are missing");
//        }
//
//        if (sgCsv == null || sgCsv.isBlank()) {
//            throw new RuntimeException("SECURITY_GROUPS env variable is not set");
//        }
//
////        String[] subnets = subnetCsv.split(",");
//        String[] securityGroups = sgCsv.split(",");
//
//
//        // ---------------- Initialize ECS client ----------------
//        AmazonECS ecs = AmazonECSClientBuilder.standard()
//                .withRegion(awsRegion)
//                .build();
//
//        try {
//            // ---------------- 1️⃣ Register Task Definition ----------------
//            ContainerDefinition container = new ContainerDefinition()
//                    .withName("app")
//                    .withImage(image)
//                    .withCpu(256)
//                    .withMemory(512)
//                    .withPortMappings(new PortMapping().withContainerPort(8080))
//                    .withEnvironment(
//                            new KeyValuePair().withName("ENV").withValue("prod"),
//                            new KeyValuePair().withName("SERVER_PORT").withValue("8080")
//                    )
//                    .withLogConfiguration(new LogConfiguration()
//                            .withLogDriver(LogDriver.Awslogs)
//                            .withOptions(Map.of(
//                                    "awslogs-group", "/ecs/java-docker-app",
//                                    "awslogs-region", awsRegion,
//                                    "awslogs-stream-prefix", "ecs"
//                            ))
//                    );
//
//            RegisterTaskDefinitionRequest taskDefRequest = new RegisterTaskDefinitionRequest()
//                    .withFamily("java-docker-app")
//                    .withNetworkMode(NetworkMode.Awsvpc)
//                    .withRequiresCompatibilities(Compatibility.FARGATE)
//                    .withCpu("256")
//                    .withMemory("512")
//                    .withContainerDefinitions(container)
//                    .withExecutionRoleArn("arn:aws:iam::450372565696:role/ECSJavaAppExecutionRole");
//
//            RegisterTaskDefinitionResult taskDefResult = ecs.registerTaskDefinition(taskDefRequest);
//            String taskDefArn = taskDefResult.getTaskDefinition().getTaskDefinitionArn();
//            System.out.println("Registered Task Definition ARN: " + taskDefArn);
//
//            // ---------------- 2️⃣ Check if ECS Service exists ----------------
//            DescribeServicesRequest describeRequest = new DescribeServicesRequest().withCluster(clusterName).withServices(serviceName);
//
//            DescribeServicesResult describeResult = ecs.describeServices(describeRequest);
//            List<Service> services = describeResult.getServices();
//            Service firstService = services.isEmpty() ? null : services.get(0);
//
//            if (firstService == null  || firstService.getStatus().equals("INACTIVE")) {
//                // ---------------- 3️⃣ Create ECS Service ----------------
//                CreateServiceRequest createRequest = new CreateServiceRequest()
//                        .withCluster(clusterName)
//                        .withServiceName(serviceName)
//                        .withTaskDefinition(taskDefArn)
//                        .withDesiredCount(1)
//                        .withLaunchType(LaunchType.FARGATE)
//                        .withNetworkConfiguration(new NetworkConfiguration()
//                                .withAwsvpcConfiguration(new AwsVpcConfiguration()
//                                        .withSubnets(subnets)
//                                        .withSecurityGroups(securityGroups)
//                                        .withAssignPublicIp("ENABLED")
//                                )
//                        );
//
//
//                ecs.createService(createRequest);
//                System.out.println("ECS Service created: " + serviceName);
//            } else {
//                // ---------------- 4️⃣ Update ECS Service ----------------
//                UpdateServiceRequest updateService = new UpdateServiceRequest()
//                        .withCluster(clusterName)
//                        .withService(serviceName)
//                        .withTaskDefinition(taskDefArn)
//                        .withForceNewDeployment(true);
//
//                ecs.updateService(updateService);
//                System.out.println("ECS Service updated successfully!");
//            }
//
//            // ---------------- 5️⃣ Wait for ECS task to get Public IP ----------------
//            logger.info("Fetching ECS task public IP...");
//            String taskArn = ecs.listTasks(new ListTasksRequest()
//                            .withCluster(clusterName)
//                            .withServiceName(serviceName))
//                    .getTaskArns()
//                    .get(0);
//            DescribeTasksResult taskDetail = ecs.describeTasks(
//                    new DescribeTasksRequest()
//                            .withCluster(clusterName)
//                            .withTasks(taskArn)
//            );
//
//            Task firstTask = taskDetail.getTasks().isEmpty() ? null : taskDetail.getTasks().get(0);
//            if (firstTask == null) {
//                throw new RuntimeException("No task details found");
//            }
//
//            List<KeyValuePair> attachmentDetails = firstTask.getAttachments().isEmpty() ? List.of() : firstTask.getAttachments().get(0).getDetails();
//
//            String publicIp = attachmentDetails.stream()
//                    .filter(d -> d.getName().equals("publicIPv4Address"))
//                    .findFirst()
//                    .orElseThrow(() -> new RuntimeException("Public IP not found"))
//                    .getValue();
//
//            String healthUrl = "http://" + publicIp + ":8080/health";
//            logger.info("Health check URL: {}", healthUrl);
//
//            // ---------------- 6️⃣ Wait for container readiness ----------------
//            waitForHealthyContainer(healthUrl); // 5 min timeout, 10 sec interval
//
//        } catch (Exception e) {
//            logger.error("Deployment failed: {}", String.valueOf(e));
//        }
//    }
//
//    private static void waitForHealthyContainer(String urlString) throws InterruptedException {
//        System.out.println("Waiting for container to become healthy at " + urlString);
//
//        HttpClient client = HttpClient.newHttpClient();
//        int elapsed = 0;
//        boolean healthy = false;
//
//        while (elapsed < 5) {
//            try {
//                HttpRequest request = HttpRequest.newBuilder()
//                        .uri(URI.create(urlString))
//                        .GET()
//                        .build();
//
//                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
//
//                if (response.statusCode() == 200) {
//                    System.out.println("Container is healthy!");
//                    healthy = true;
//                    break;
//                } else {
//                    System.out.println("Health check returned status " + response.statusCode() + ". Retrying...");
//                }
//            } catch (Exception e) {
//                System.out.println("Health check failed: " + e.getMessage() + ". Retrying...");
//            }
//
//            Thread.sleep(10 * 1000L);
//            elapsed += 10;
//        }
//
//        if (!healthy) {
//            throw new RuntimeException("Deployment timeout: container did not become healthy within " + 5 + " seconds.");
//        }
//    }
//
//}
//

package devlab.sudip.java_deploy;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.AmazonECSClientBuilder;
import com.amazonaws.services.ecs.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public class ECSDeployer {

    private static final Logger logger = LoggerFactory.getLogger(ECSDeployer.class);

    public static void main(String[] args) {

        // ---------- ENV CONFIG ----------
        String clusterName = requireEnv("ECS_CLUSTER");
        String serviceName = requireEnv("ECS_SERVICE");
        String image = requireEnv("DOCKER_IMAGE");
        String region = requireEnv("AWS_REGION");
        String[] subnets = requireEnv("SUBNETS").split(",");
        String[] securityGroups = requireEnv("SECURITY_GROUPS").split(",");

        AmazonECS ecs = AmazonECSClientBuilder.standard()
                .withRegion(region)
                .build();

        try {
            // ---------- 1️⃣ Register Task Definition ----------
            ContainerDefinition container = new ContainerDefinition()
                    .withName("app")
                    .withImage(image)
                    .withCpu(256)
                    .withMemory(512)
                    .withPortMappings(new PortMapping().withContainerPort(8080))
                    .withLogConfiguration(new LogConfiguration()
                            .withLogDriver(LogDriver.Awslogs)
                            .withOptions(Map.of(
                                    "awslogs-group", "/ecs/java-docker-app",
                                    "awslogs-region", region,
                                    "awslogs-stream-prefix", "ecs"
                            )));

            RegisterTaskDefinitionRequest taskRequest =
                    new RegisterTaskDefinitionRequest()
                            .withFamily("java-docker-app")
                            .withNetworkMode(NetworkMode.Awsvpc)
                            .withRequiresCompatibilities(Compatibility.FARGATE)
                            .withCpu("256")
                            .withMemory("512")
                            .withExecutionRoleArn(
                                    "arn:aws:iam::450372565696:role/ECSJavaAppExecutionRole"
                            )
                            .withContainerDefinitions(container);

            String taskDefArn = ecs.registerTaskDefinition(taskRequest)
                    .getTaskDefinition()
                    .getTaskDefinitionArn();

            logger.info("Task definition registered: {}", taskDefArn);

            // ---------- 2️⃣ Create or Update Service ----------
            List<Service> services = ecs.describeServices(
                    new DescribeServicesRequest()
                            .withCluster(clusterName)
                            .withServices(serviceName)
            ).getServices();

            if (services.isEmpty() || "INACTIVE".equals(services.get(0).getStatus())) {

                ecs.createService(new CreateServiceRequest()
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
                                )));

                logger.info("ECS Service created: {}", serviceName);

            } else {
                ecs.updateService(new UpdateServiceRequest()
                        .withCluster(clusterName)
                        .withService(serviceName)
                        .withTaskDefinition(taskDefArn)
                        .withForceNewDeployment(true));

                logger.info("ECS Service updated: {}", serviceName);
            }

            // ---------- 3️⃣ Wait for Task ----------
            String taskArn = waitForTask(ecs, clusterName, serviceName);
            Task task = waitForTaskRunning(ecs, clusterName, taskArn);

            // ---------- 4️⃣ Fetch Public IP ----------
            String publicIp = waitForPublicIp(task);
            String healthUrl = "http://" + publicIp + ":8080/health";

            logger.info("Health URL: {}", healthUrl);

            // ---------- 5️⃣ Health Check ----------
            waitForHealthyContainer(healthUrl);

            logger.info("🚀 Deployment SUCCESSFUL");

        } catch (Exception e) {
            logger.error("Deployment failed", e);
            System.exit(1);
        }
    }

    // ================= HELPERS =================

    private static String requireEnv(String key) {
        String val = System.getenv(key);
        if (val == null || val.isBlank()) {
            throw new IllegalArgumentException("Missing env var: " + key);
        }
        return val;
    }

    private static String waitForTask(AmazonECS ecs, String cluster, String service)
            throws InterruptedException {

        for (int i = 0; i < 30; i++) {
            List<String> tasks = ecs.listTasks(new ListTasksRequest()
                    .withCluster(cluster)
                    .withServiceName(service)
            ).getTaskArns();

            if (!tasks.isEmpty()) {
                return tasks.get(0);
            }
            Thread.sleep(5000);
        }
        throw new RuntimeException("No ECS task started");
    }

    private static Task waitForTaskRunning(
            AmazonECS ecs, String cluster, String taskArn
    ) throws InterruptedException {

        for (int i = 0; i < 30; i++) {
            Task task = ecs.describeTasks(new DescribeTasksRequest()
                    .withCluster(cluster)
                    .withTasks(taskArn)
            ).getTasks().get(0);

            if ("RUNNING".equals(task.getLastStatus())) {
                return task;
            }
            Thread.sleep(5000);
        }
        throw new RuntimeException("Task did not reach RUNNING");
    }

    private static String waitForPublicIp(Task task) throws InterruptedException {

        for (int i = 0; i < 20; i++) {
            for (KeyValuePair kv :
                    task.getAttachments().get(0).getDetails()) {

                if ("publicIPv4Address".equals(kv.getName())) {
                    return kv.getValue();
                }
            }
            Thread.sleep(5000);
        }
        throw new RuntimeException("Public IP not assigned");
    }

    private static void waitForHealthyContainer(String url)
            throws Exception {

        HttpClient client = HttpClient.newHttpClient();

        for (int i = 0; i < 30; i++) {
            try {
                HttpResponse<Void> response =
                        client.send(HttpRequest.newBuilder()
                                        .uri(URI.create(url))
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.discarding());

                if (response.statusCode() == 200) {
                    logger.info("Container is healthy");
                    return;
                }
            } catch (Exception ignored) {}

            Thread.sleep(10_000);
        }
        throw new RuntimeException("Health check failed");
    }
}
