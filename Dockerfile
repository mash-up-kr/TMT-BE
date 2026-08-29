FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY tmt-bootstrap/build/libs/*.jar app.jar

# 총량은 그대로 두고 배분만 바꾼다 — t3.micro(916MB)에 Caddy·OS가 함께 올라가 있어
# 총합을 늘리면 이번엔 컨테이너가 OOMKilled된다.
# Metaspace 128m는 S3 SDK(TMT-202)·LLM(TMT-232) 모듈이 들어오면서 한계를 넘었다 (TMT-253).
ENV JAVA_OPTS="-XX:+UseG1GC \
  -XX:+UseContainerSupport \
  -Xms256m -Xmx288m \
  -XX:ReservedCodeCacheSize=96m \
  -XX:MaxMetaspaceSize=256m \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:+ExitOnOutOfMemoryError \
  -XX:+UseStringDeduplication \
  -Dfile.encoding=UTF-8"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
