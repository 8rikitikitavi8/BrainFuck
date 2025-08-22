# IBM MQ Routing Stub (Java)

Конфигурируемая заглушка IBM MQ на Java (JMS), слушает очереди и пересылает сообщения на другие очереди, поддерживает TLS и без TLS.

## Требования
- JDK 11+
- Maven 3.8+
- IBM MQ JMS allclient (`com.ibm.mq:com.ibm.mq.allclient`) подтянется автоматически, но для работы TLS нужны truststore/keystore

## Сборка
```bash
cd java-mq-stub
mvn -q -e -DskipTests package
```
Готовый jar: `target/mq-stub-0.1.0-shaded.jar`

## Конфигурация
См. `config.example.yaml`.
- Брокеры: `queue_manager`, `channel`, `connection_name`, `user/password`, `tls { enabled, cipher, truststore, truststore_password, keystore, keystore_password }`
- Маршруты: `from/source { broker, queue }`, `to/target { broker, queue }`, таймауты

## Запуск
```bash
java -jar target/mq-stub-0.1.0-shaded.jar --config ./config.example.yaml
```
Проверка конфига:
```bash
java -jar target/mq-stub-0.1.0-shaded.jar --config ./config.example.yaml --dry-run
```

## Примечания
- Для TLS укажите корректные пути к JKS и шифр (`setSSLCipherSuite`). Если используете стандартные названия cipher из OpenJDK, свойство `com.ibm.mq.cfg.useIBMCipherMappings=false` уже выставляется.
- Пересылка осуществляется как bytes/text без преобразования MQRFH2 и т.д.