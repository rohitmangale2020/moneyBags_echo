# Product Admin UI

Oracle JET JavaScript application for Product Service administration.

## Run locally

Start Product Service on port `8081`, then run:

```powershell
npx ojet serve
```

The JET development server uses `http://localhost:8000`, which Product Service allows through its development CORS configuration.

The initial UI uses the temporary `admin` Basic Auth account automatically. This is development-only; replace it with the bank's OAuth2/JWT login before deployment.
