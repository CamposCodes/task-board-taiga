# syntax=docker/dockerfile:1
FROM node:24-alpine AS build
WORKDIR /src

COPY --from=certs . /certs/
RUN cat /certs/*.crt > /tmp/extra-ca.pem 2>/dev/null; true
ENV NODE_EXTRA_CA_CERTS=/tmp/extra-ca.pem

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund --ignore-scripts
COPY . .
RUN npm run build

FROM node:24-alpine
WORKDIR /app
COPY --from=build /src/.output ./
USER node
ENV NODE_ENV=production HOST=0.0.0.0 PORT=3000
EXPOSE 3000
HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=5 \
  CMD wget -qO /dev/null http://127.0.0.1:3000/login || exit 1
CMD ["node", "server/index.mjs"]
