# ddak-ta

This project uses [Gradle](https://gradle.org/).
To build and run the application, use the *Gradle* tool window by clicking the Gradle icon in the
right-hand toolbar,
or run it directly from the terminal:

* Run `./gradlew run` to build and run the application.
* Run `./gradlew build` to only build the application.
* Run `./gradlew check` to run all checks, including tests.
* Run `./gradlew clean` to clean all build outputs.

Note the usage of the Gradle Wrapper (`./gradlew`).
This is the suggested way to use Gradle in production projects.

[Learn more about the Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).

[Learn more about Gradle tasks](https://docs.gradle.org/current/userguide/command_line_interface.html#common_tasks).

This project follows the suggested multi-module setup and consists of the `app` and `utils`
subprojects.
The shared build logic was extracted to a convention plugin located in `buildSrc`.

This project uses a version catalog (see `gradle/libs.versions.toml`) to declare and version
dependencies
and both a build cache and a configuration cache (see `gradle.properties`).

## Matching Service Architecture

다음은 승객의 배차 요청부터 드라이버 매칭까지 이어지는 `matching-service`의 핵심 아키텍처 흐름을 나타낸 시퀀스 다이어그램입니다.

```mermaid
sequenceDiagram
    actor Passenger
    participant API Gateway
    participant Ride Service
    participant Matching Service
    participant Location Service
    participant Kafka
    participant Redis
    participant DB

    Passenger->>+API Gateway: POST /api/v1/rides (운행 요청)
    API Gateway->>+Ride Service: createRide(request)

    Ride Service->>+Redis: Check duplicate request
    Redis-->>-Ride Service: OK
    
    Ride Service->>+DB: Create Ride (status: REQUESTED)
    DB-->>-Ride Service: Ride created
    
    Ride Service->>-Kafka: Publish [RideRequestedEvent]
    API Gateway-->>-Passenger: HTTP 201 CREATED (요청 성공)

    Note right of Kafka: -- 비동기 처리 시작 --

    participant Scheduler
    Scheduler->>+Matching Service: trigger: processMatchingBatch()

    Matching Service->>+Redis: Acquire Distributed Lock
    Redis-->>-Matching Service: Lock acquired

    Matching Service->>+DB: Get pending requests (H3 그룹핑)
    DB-->>-Matching Service: Requests list

    Matching Service->>+Location Service: findNearbyDrivers(h3Index)
    Location Service-->>-Matching Service: Available drivers list

    Matching Service->>Matching Service: 비용 행렬 생성 및 헝가리안 알고리즘 실행
    
    Matching Service->>+DB: Update Ride (status: MATCHED)
    DB-->>-Matching Service: Ride updated

    Matching Service->>-Kafka: Publish [RideMatchedEvent]
    
    Matching Service->>+Redis: Release Distributed Lock
    Redis-->>-Matching Service: Lock released
```