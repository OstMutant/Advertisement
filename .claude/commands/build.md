Rebuild the marketplace-app Docker image and start a fresh container.

Steps:
1. Stop and remove existing container: `docker rm -f marketplace-app`
2. Build image (this takes a few minutes): `docker build -f Dockerfile -t marketplace-app .`
3. Start container:
```
docker run -d --name marketplace-app --network host \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=localhost -e DB_PORT=5432 -e DB_NAME=experiments \
  -e DB_USER=experiments_user -e DB_PASSWORD=experiments_user_password \
  -e S3_ENDPOINT=http://localhost:9000 -e S3_BUCKET=advertisement \
  -e S3_ACCESS_KEY=admin -e S3_SECRET_KEY=admin12345 \
  -e S3_REGION=us-east-1 -e S3_PUBLIC_URL=http://localhost:9000/advertisement \
  marketplace-app
```
4. Monitor startup: run `docker logs -f marketplace-app` with run_in_background: true,
   then use the Monitor tool and wait for "Started Application" in the output
5. Report success and confirm the app is ready for Playwright tests
