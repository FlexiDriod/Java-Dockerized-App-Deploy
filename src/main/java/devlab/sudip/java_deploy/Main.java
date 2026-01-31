// ------------------------------------------------------------------------
// Enterprise CI/CD Platform for Containerized Java Microservices on AWS
// ------------------------------------------------------------------------
package devlab.sudip.java_deploy;
//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        /*
         *
                Developer
                   ↓
                GitHub (PR / Push)
                   ↓
                GitHub Actions CI/CD
                   ├─ Code Quality Checks
                   ├─ Maven Build
                   ├─ Docker Multi-stage Build
                   ├─ Image Scan (optional)
                   └─ Push to ECR
                       ↓
                AWS ECS Fargate
                   ├─ Auto Scaling
                   ├─ Health Checks
                   ├─ Rolling Deployments
                   └─ Application Load Balancer
                       ↓
                CloudWatch Logs & Metrics
      */
    }
}

