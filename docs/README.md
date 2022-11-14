# Cloud-native Experience Lab Workshop (for JavaEE / Microprofile)

This lab will focus on the development and deployment of the Cloud-native weather service application
in JavaEE and Microprofile. It will take you through the relevant phases and tasks reuired to go from source code to a 
fully deployed application on an integration environment.

## Prerequisites

Before you dive right into Cloud-native development with JavaEE and Microprofile, make sure your local development
environment is setup properly! 

- Modern Operating System (Windows 10, MacOS, ...) with terminal and shell
- IDE of your personal choice (with relevant plugins installed)
  - IntelliJ Ultimate
  - VS Code
- Local Docker / Kubernetes installation (Docker Desktop, Rancher Desktop, Minikube)
- [JDK 11](https://adoptium.net/)
- [Kustomize](https://kustomize.io)
- [Tilt](https://tilt.dev)
- [Flux2](https://tilt.dev)

## Project setup

The focus of this lab is not on the actual implementation of the service itself. However, kicking off
a cloud-native project in JavaEE is pretty straight forward. Simply use https://start.microprofile.io/

With this, you can now start to implement the required business logic of the weather service application.

## Crosscutting Concerns

According to the [12-factor app principles](https://12factor.net), there are many cross-cutting concenrs that need to be addressed by a cloud-native application: configuration, observability and many more.

### Configuration

The easiest option to introduce configurability is via ENV variables. So instead of using hard-coded
configuration values, a default value should always be superceded by the environment value.

**Lab Instructions**
1. Add support for specific PostgreSQL configuration parameters (host, DB, username, password) using ENV variables
2. (_optional_) Add support for specific OpenWeatherMap parameters using ENV variables

<details>
  <summary markdown="span">Click to expand solution ...</summary>

  _TODO_ show web.xml snippet

</details>

### Observability

The 3 pillars of good observability are: Logging, Metrics and Tracing. Using the appropriate Microprofile
dependencies these traits can be introduced pretty easily into the weather service application.

**Lab Instructions**
1. Expose a /metrics endpoint that exposes Prometheus compatible data
2. (_optional_) Introduce and emit OpenTelemetry tracing data
3. (_optional_) Introduce and use JSON structured logging as output

<details>
  <summary markdown="span">Click to expand solution ...</summary>

  _TODO_ add MP dependencies for health, metrics and tracing

</details>

## Containerization

In this step we now need to containerize the application. We can leverage a multi-stage approach:
the final runtime artifact is build during the image build stage and then used and copied in the final runtime
stage (very similar to Cloud-native Build Packs approach).

**Lab Instructions**
1. Create a `Dockerfile` and add the following stage
    - Assemble the final runtime image using `gcr.io/distroless/java:11` as base
2. (_optional_) Implement a Docker multi-stage build
    - Build the JavaEE WAR artefact using the correct `openjdk:11` base
    - Assemble the final runtime image using the WAR artefact from the previous stage

<details>
  <summary markdown="span">Click to expand solution ...</summary>

```
FROM openjdk:11 as builder

WORKDIR /javaee

COPY .mvn ./.mvn/
COPY mvnw ./
COPY pom.xml ./

RUN ./mvnw dependency:resolve dependency:resolve-plugins

COPY src src/
RUN ./mvnw package -DskipTests

FROM gcr.io/distroless/java:11

COPY --from=amd64/busybox:1.31.1 /bin/busybox /busybox/busybox
RUN ["/busybox/busybox", "--install", "/bin"]

WORKDIR /payara

COPY --from=payara/micro:5.2022.3 /opt/payara/payara-micro.jar ./

# https://blog.payara.fish/warming-up-payara-micro-container-images-in-5.201
RUN java -jar payara-micro.jar --rootdir micro-root --outputlauncher && \
    rm -rf payara-micro.jar && \
    java -XX:DumpLoadedClassList=classes.lst -jar micro-root/launch-micro.jar --warmup && \
    java -Xshare:dump -XX:SharedClassListFile=classes.lst -XX:SharedArchiveFile=payara.jsa -jar micro-root/launch-micro.jar && \
    rm -rf classes.lst

EXPOSE 8080

ENTRYPOINT ["java", "-server", "-Xshare:on", "-XX:SharedArchiveFile=payara.jsa", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-XX:ThreadStackSize=256", "-XX:MaxMetaspaceSize=128m", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=250", "-XX:+UseStringDeduplication", "-Djava.security.egd=file:/dev/./urandom", "-jar", "micro-root/launch-micro.jar"]
CMD ["--nocluster", "--disablephonehome", "--deploy", "weather-service-javaee.war:/"]

COPY --from=builder /javaee/target/weather-service-javaee.war ./
```

</details>

## K8s Deployment

The application needs to be deployed, configured, run and exposed with Kubernetes. Several competing 
approaches exist, namely Kustomize and Helm. For this section, you only need to choose and use one of them.

### Option a) Kustomize

In this step we are going to use [Kustomize](https://kustomize.io) to handle the Kubernetes manifests required
to deploy, configure and expose the application to multiple Kubernetes environments.

**Lab Instructions**
1. Create directory structure for Kustomize `base/` and overlays for `dev/` and `prod/`
2. Create base Kubernetes resources for the application and adjust Kustomization
    - Create `Deployment` resource for application and add resource to `kustomization.yaml`
    - Create `Service` resource for application and add resource to `kustomization.yaml`
    - Create `ConfigMap` and `Secret` using Kustomize generators
3. Create PostgreSQL `Deployment` and `Service` resources in the `dev/` overlay and adjust Kustomization
4. Create the following Kustomize patches for the application in the `prod/` overlay
    - Patch the deployment and set `replicas: 2` with a dedicated file
    - Patch the service and set `type: LoadBalancer` as Json6902 patch file

<details>
  <summary markdown="span">Click to expand solution ...</summary>

The directory structure for the base and overlay Kustomization should follow the suggested [common layout](https://kubectl.docs.kubernetes.io/references/kustomize/glossary/#kustomization-root)

```bash
# create the suggested directory layout
mkdir -p k8s/base
mkdir -p k8s/overlays/dev
mkdir -p k8s/overlays/prod

# create initial kustomization.yaml
cd k8s/base && kustomize create && cd ...
cd k8s/overlays/dev && kustomize create && cd ....
cd k8s/overlays/prod && kustomize create && cd ....
```

Next, we create the **base** Kubernetes resources for the application and register these with the Kustomization.
```yaml
# add this to a new base/microservice-deployment.yaml file
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: weather-service
  labels:
    type: microservice
spec:
  replicas: 1
  selector:
    matchLabels:
      app: weather-service
  template:
    metadata:
      labels:
        app: weather-service
    spec:
      containers:
      - name: weather-service
        image: cloud-native-weather-javaee
        resources:
          requests:
            memory: "256Mi"
            cpu: "0.5"
          limits:
            memory: "512Mi"
            cpu: "2"
        ports:
          - name: http
            containerPort: 8080
        envFrom:
          - configMapRef:
              name: database-configmap
          - secretRef:
              name: database-secrets

# add this to a new base/microservice-service.yaml file
---
apiVersion: v1
kind: Service
metadata:
  name: weather-service
  labels:
    type: microservice
spec:
  selector:
    app: weather-service
  type: ClusterIP
  sessionAffinity: None
  ports:
    - protocol: TCP
      port: 8080
      targetPort: http

# add these to the base/kustomization.yaml
---
commonLabels:
  app: weather-service
  framework: javaee

buildMetadata: [managedByLabel]

resources:
  - microservice-deployment.yaml
  - microservice-service.yaml
```

The `ConfigMap` and `Secret` resources required to configure the application are generated by Kustomize.
Add the following definitions to the `base/kustomization.yaml`
```yaml
configMapGenerator:
  - name: database-configmap
    literals:
      - javaee_DATASOURCE_URL=jdbc:postgresql://weather-database:5432/weather
      - POSTGRES_HOST=weather-database
      - POSTGRES_DB=weather

secretGenerator:
  - name: database-secrets
    literals:
      - POSTGRES_USER=
      - javaee_DATASOURCE_USERNAME=
      - POSTGRES_PASSWORD=
      - javaee_DATASOURCE_PASSWORD=
```

The **dev** overlay needs to define the Kubernetes resources for a locally deployed PostgreSQL database. The
`Deployment` and `Service` definitions need to be registered inside the `overlays/dev/kustomization.yaml`.

```yaml
# add this to a new overlays/dev/database-deployment.yaml file
---
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    type: database
  name: weather-database
spec:
  replicas: 1
  selector:
    matchLabels:
      type: database
  template:
    metadata:
      labels:
        type: database
    spec:
      containers:
        - name: database
          image: postgres:11.16
          imagePullPolicy: "IfNotPresent"
          resources:
            requests:
              memory: "128Mi"
              cpu: "0.5"
            limits:
              memory: "256Mi"
              cpu: "0.5"
          ports:
            - containerPort: 5432
          envFrom:
            - configMapRef:
                name: database-configmap
            - secretRef:
                name: database-secrets

# add this to the overlays/dev/database-service.yaml file
---
apiVersion: v1
kind: Service
metadata:
  labels:
    type: database
  name: weather-database
spec:
  ports:
    - name: "5432"
      port: 5432
      targetPort: 5432
  selector:
    type: database

# add these to the overlays/dev/kustomization.yaml
---
resources:
  # you can also specify a Git repo URL here
  - ../../base/
  - database-deployment.yaml
  - database-service.yaml

configMapGenerator:
  - name: database-configmap
    behavior: merge
    literals:
      - javaee_DATASOURCE_URL=jdbc:postgresql://weather-database:5432/weather
      - POSTGRES_HOST=weather-database
      - POSTGRES_DB=weather

secretGenerator:
  - name: database-secrets
    behavior: merge
    literals:
      - POSTGRES_USER=javaee
      - javaee_DATASOURCE_USERNAME=javaee
      - POSTGRES_PASSWORD=1qay2wsx
      - javaee_DATASOURCE_PASSWORD=1qay2wsx
```

For the **prod** overlay we need to patch the **base** Kubernetes resources to only modify certain fields, like 
replica count of the `Deployment` or the `Service` type.

```yaml
# add the following YAML patch to the overlays/prod/2-replicas.yaml file
# the resource is identified by apiVersion + kind + name, everything under spec will be patched
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: weather-service
spec:
  replicas: 2

# add the following JSON 6902 patch to the overlays/prod/loadbalancer.yaml
---
- op: replace
  path: /spec/type
  value: LoadBalancer

# register the patches in the overlays/prod/kustomization.yaml
---
patchesStrategicMerge:
  - 2-replicas.yaml

patchesJson6902:
  - target:
      version: v1
      kind: Service
      name: weather-service
    path: loadbalancer.yaml
```
</details>

### Option b) Helm Chart

_TODO_

<details>
  <summary markdown="span">Click to expand solution ...</summary>

```bash
# prepare the gh-pages branch to serve the Helm chart
git checkout --orphan gh-pages
git reset --hard
git commit --allow-empty -m "fresh and empty gh-pages branch"
git push origin gh-pages
```
</details>

## Continuous Development

A good and efficient developer experience (DevEx) is of utmost importance for any cloud-native software
engineer. Rule 1: stay local as long as possible. Rule 2: automate all required steps: compile and package the source code, containerize the artifact and deploy the Kubernetes resources locally. Continuously. The tools _Tilt_ and _Skaffold_ can both be used to establish this continuous dev-loop. For this section, you
only need to choose and use one of them.

### Option a) Tilt

In this step we are going to use [Tilt](https://tilt.dev) to build, containerize and deploy the application
continuously to a local Kubernetes environment.

**Lab Instructions**
1. Make sure Tilt is installed locally on the development machine
2. Write a `Tiltfile` that performs the following steps
    - Build the required Docker image on every change to the source code
    - Apply the DEV overlay resources using Kustomize
    - Create a local port forward to the weather service HTTP port

<details>
  <summary markdown="span">Click to expand solution ...</summary>

Depending on your local K8s environment, the final `Tiltfile` might look slighty different.
```python
# -*- mode: Python -*-
# allow_k8s_contexts('rancher-desktop')

local_resource('weather-service-build', './mvnw package -DskipTests', dir='.', deps=['./pom.xml', './src/'], labels=['JavaEE'])

# to disable push with rancher desktop we need to use custom_build instead of docker_build
# docker_build('cloud-native-weather-javaee', '.', dockerfile='Dockerfile', only=['./target/'])
custom_build('cloud-native-weather-javaee', 'docker build -t $EXPECTED_REF .', ['./target/'], disable_push=True)

k8s_yaml(kustomize('./k8s/overlays/dev/'))
k8s_resource(workload='weather-service', port_forwards=[port_forward(18080, 8080, 'HTTP API')], labels=['javaee'])
```

To see of everything is working as expected issue the following command: `tilt up`
</details>

### Option b) Skaffold

In this step we are going to use [Skaffold](https://skaffold.dev) to build, containerize and deploy the application
continuously to a local Kubernetes environment.

**Lab Instructions**
1. Make sure Skaffold is installed locally on the development machine
2. Write a `skaffold.yaml` that performs the following steps
    - Build the required Docker image on every change to the source code
    - Apply the DEV overlay resources using Kustomize
    - Create a local port forward to the weather service HTTP port

<details>
  <summary markdown="span">Click to expand solution ...</summary>

The 3 steps of building, deployment and port-forwarding can all be codified in the
`skaffold.yaml` descriptor file.

```yaml
apiVersion: skaffold/v2beta24
kind: Config
metadata:
  name: cloud-native-weather-javaee

build:
  tagPolicy:
    gitCommit: {}
  artifacts:
    - image: cloud-native-weather-javaee
      docker:
        dockerfile: Dockerfile
  local:
    push: false
    useBuildkit: true
    useDockerCLI: false

deploy:
  kustomize:
    defaultNamespace: default
    paths: ["k8s/overlays/dev"]

portForward:
  - resourceName: weather-service
    resourceType: service
    namespace: default
    port: 8080
    localPort: 18080
```

To see of everything is working as expected issue the following command: `skaffold dev --no-prune=false --cache-artifacts=false`

</details>

## Continuous Integration

For any software project there must be a CI tool that takes care of continuously building and testing the produced software artifacts on every change.

### Github Actions

In this step we are going to use Github actions to build and test the application on every change. Also we are going to
leverage Github actions to perform 3rd party dependency checks as well as building and pushing the Docker image.

**Lab Instructions**
1. Create a Github action for each of the following tasks
    - Build the project on every change on main branch and every pull request
    - Build and push the Docker image to Github packages main branch, every pull request and tags
    - (_optional_) Perform CodeQL scans on main branch and every pull request
    - (_optional_) Perform a dependency review on every pull request

<details>
  <summary markdown="span">Click to expand solution ...</summary>

For each of the tasks, open the Github actions tab for the repository in your browser. Choose 'New workflow'. 

In the list of predefined actions, choose the **Java with Maven** action. Adjust the suggested YAML
file content and commit.
```yaml
name: Java CI with Maven

on:
  push:
    branches: [ "main" ]
  pull_request:
    branches: [ "main" ]

jobs:
  build:

    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v3

    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: maven

    - name: Build with Maven
      run: ./mvnw -B package --file pom.xml
```

Next, choose the **Publish Docker Container** action from the Continuous integration section. Adjust the suggested YAML file content and commit.
```yaml
name: 'Docker Publish'

on:
  push:
    branches: [ "main" ]
    tags: [ 'v*.*.*' ]
  pull_request:
    branches: [ "main" ]

env:
  # Use docker.io for Docker Hub if empty
  REGISTRY: ghcr.io
  # github.repository as <account>/<repo>
  IMAGE_NAME: ${{ github.repository }}

jobs:
  build:

    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
      # This is used to complete the identity challenge
      # with sigstore/fulcio when running outside of PRs.
      id-token: write

    steps:
      - name: Checkout repository
        uses: actions/checkout@v3
        
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven

      - name: Build with Maven
        run: ./mvnw -B package --file pom.xml

      # Install the cosign tool except on PR
      # https://github.com/sigstore/cosign-installer
      - name: Install cosign
        if: github.event_name != 'pull_request'
        uses: sigstore/cosign-installer@main
        with:
          cosign-release: 'v1.9.0'

      # Workaround: https://github.com/docker/build-push-action/issues/461
      - name: Setup Docker buildx
        uses: docker/setup-buildx-action@v2

      # Login against a Docker registry except on PR
      # https://github.com/docker/login-action
      - name: Log into registry ${{ env.REGISTRY }}
        if: github.event_name != 'pull_request'
        uses: docker/login-action@v2
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      # Extract metadata (tags, labels) for Docker
      # https://github.com/docker/metadata-action
      - name: Extract Docker metadata
        id: meta
        uses: docker/metadata-action@v4
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
          tags: |
            type=semver,pattern={{version}}
            type=semver,pattern={{major}}.{{minor}}
            type=semver,pattern={{major}}
            type=ref,event=branch
            type=raw,value=latest,enable={{is_default_branch}}

      # Build and push Docker image with Buildx (don't push on PR)
      # https://github.com/docker/build-push-action
      - name: Build and push Docker image
        id: build-and-push
        uses: docker/build-push-action@v3
        with:
          context: .
          push: ${{ github.event_name != 'pull_request' }}
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
```

Now repeat this process for the remaining two optional CI tasks of this lab.
</details>

## Continuous Deployment

## Flux2

In this step we are going to deploy the application using Flux2 onto the lab cluster. Flux will manage
the whole lifecycle, from initial deployment to automatic updates in case of changes and new versions.

**Lab Instructions**
1. Clone the Gitops repository for your cluster and create a dedicated apps directory
2. Create a dedicated K8s namespace resource
3. Install the weather service into the apps namespace using Kustomize
    - Patch the deployment and set `replicas: 2`
    - Patch the service and set `type: LoadBalancer`
4. (_optional_) Setup the image update automation workflow with suitable image repository and policy

<details>
  <summary markdown="span">Click to expand solution ...</summary>

First, we need to onboard and integrate the application with the Gitops workflow and repository.
```bash
# clone the experience lab Gitops repository (or create a dedicated repo)
git clone https://github.com/qaware/cloud-native-explab.git
# create dedicated apps directory
take applications/gcp/cloud-native-explab/weather-service-javaee/
# initialize Kustomize descriptor
kustomize create
```

Create a `weather-namespace.yaml` file with the following content in the apps GitOps directory.
Do not forget to register the file resource in your `kustomization.yaml`.
```yaml
kind: Namespace
apiVersion: v1
metadata:
    name: weather-javaee
```

Next, create the relevant Flux2 resources, such as `GitRepository` and `Kustomization` for the application.
```bash
flux create source git cloud-native-weather-javaee \
    --url=https://github.com/qaware/cloud-native-weather-javaee \
    --branch=main \
    --interval=5m0s \
    --export > weather-source.yaml

flux create kustomization cloud-native-weather-javaee \
    --source=GitRepository/cloud-native-weather-javaee \
    --path="./k8s/overlays/dev" \
    --prune=true \
    --interval=5m0s \
    --target-namespace=weather-javaee \
    --export > weather-kustomization.yaml
```

The desired environment specific patches need to be added manually to the `weather-kustomization.yaml`, e.g.
```yaml
  images:
    - name: cloud-native-weather-javaee
      newName: ghcr.io/qaware/cloud-native-weather-javaee # {"$imagepolicy": "flux-system:cloud-native-weather-javaee:name"}
      newTag: 1.1.0 # {"$imagepolicy": "flux-system:cloud-native-weather-javaee:tag"}
  patchesStrategicMerge:
    - apiVersion: apps/v1
      kind: Deployment
      metadata:
        name: weather-service
      spec:
        replicas: 2
    - apiVersion: v1
      kind: Service
      metadata:
        name: weather-service
      spec:
        type: LoadBalancer
```

Finally, add and configure image repository and policy for the image update automation to work.
```bash
flux create image repository cloud-native-weather-javaee \
    --image=ghcr.io/qaware/cloud-native-weather-javaee \
    --interval 1m0s \
    --export > weather-registry.yaml

flux create image policy cloud-native-weather-javaee \
    --image-ref=cloud-native-weather-javaee \
    --select-semver=">=1.2.0 <2.0.0" \
    --export > weather-policy.yaml
```

Once all files have been created and modified, Git commit and push everything and watch the cluster
and Flux do the magic.

```bash
# to manually trigger the GitOps process use the following commands
flux reconcile source git flux-system
flux reconcile kustomization applications
flux get all
```
</details>