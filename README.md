# Proxy pattern backend

Spring Boot API for a microservice monitor. Three services (Inventory, Orders, Payments) sit behind a logging proxy that records usage, response times and errors for every call.

## Proxy pattern

| Role | Class |
|---|---|
| Subject | `MicroserviceProxy<T>` with `execute(String operation, Object... params): T` |
| RealSubject | `InventoryService`, `OrderService`, `PaymentService` |
| Proxy | `LoggingProxy<T>` wraps any `MicroserviceProxy<T>` |
| Client | `ServiceController`, `LoadSimulator` |

`ProxyConfig` builds each real service inside its proxy bean, so clients can only reach a service through its proxy.

## Endpoints

| Method | Path |
|---|---|
| POST | `/api/services/inventory/{list,check,reserve,restock}` |
| POST | `/api/services/orders/{create,get,list,cancel}` |
| POST | `/api/services/payments/{charge,status,refund}` |
| GET | `/api/metrics/summary` |
| GET | `/api/metrics/logs?service=&status=&from=&to=&page=&size=` |
| POST | `/api/metrics/simulate-load` |

10% of the calls to `PaymentService` fail on purpose. Logs live in memory and reset on redeploy.

## Run

```bash
mvn test
mvn spring-boot:run
```

`FRONTEND_ORIGIN` sets the allowed CORS origin.
