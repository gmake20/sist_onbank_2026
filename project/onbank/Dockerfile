# GitHub Actions에서 ./gradlew build 로 만든 jar를 실행만 하는 이미지
# (빌드는 Actions에서 하므로 여기서는 JRE만 사용)
FROM eclipse-temurin:21-jre

# 한국 시간 (로그, LocalDateTime.now() 등)
ENV TZ=Asia/Seoul

WORKDIR /app

# build.gradle 에서 plain jar(jar.enabled=false)를 끄므로 bootJar 하나만 생성됨
COPY build/libs/*.jar app.jar

# application-local.properties 는 이미지에 넣지 않고 서버의 파일을 /app/config 로 마운트한다
# (Spring Boot는 ./config/ 폴더의 설정 파일을 자동으로 읽음)
EXPOSE 8000

ENTRYPOINT ["java", "-jar", "app.jar"]
