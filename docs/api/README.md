# API testing

## Swagger UI

Start the backend and open:

`http://localhost:8080/api/swagger-ui.html`

The OpenAPI document is available at `/api/openapi.yaml`. Use **Authorize** in
Swagger UI and enter `Bearer <access-token>` after calling the login endpoint.

## Role smoke test

The API has four roles: `CENTRAL_ADMIN`, `PROVINCE_ADMIN`, `LOCAL_BODY_ADMIN`,
and `WARD_ADMIN`. There is no user-account registration endpoint. Citizen
registration is `/v1/citizens/register` and is intentionally allowed only for
`WARD_ADMIN` and `LOCAL_BODY_ADMIN`.

Install `curl` and `jq`, export credentials, then run:

```bash
export CENTRAL_ADMIN_EMAIL=central@example.test CENTRAL_ADMIN_PASSWORD='...'
export PROVINCE_ADMIN_EMAIL=province@example.test PROVINCE_ADMIN_PASSWORD='...'
export LOCAL_BODY_ADMIN_EMAIL=local@example.test LOCAL_BODY_ADMIN_PASSWORD='...'
export WARD_ADMIN_EMAIL=ward@example.test WARD_ADMIN_PASSWORD='...'
export WARD_ID=00000000-0000-0000-0000-000000000001
./scripts/api-role-smoke-test.sh
```

Set `BASE_URL` when the application uses another host or port. The script never
prints passwords and does not remove or mutate containers.