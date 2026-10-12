# syntax=docker/dockerfile:1
FROM node:24-alpine AS build
WORKDIR /src

COPY --from=certs . /certs/
RUN cat /certs/*.crt > /tmp/extra-ca.pem 2>/dev/null; true
ENV NODE_EXTRA_CA_CERTS=/tmp/extra-ca.pem

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY tsconfig.json ./
COPY src src
RUN npm run build && npm prune --omit=dev

FROM node:24-alpine
WORKDIR /app
COPY --from=build /src/node_modules node_modules
COPY --from=build /src/dist dist
COPY package.json ./
USER node
ENV NODE_ENV=production HOST=0.0.0.0 PORT=3333
EXPOSE 3333
CMD ["node", "dist/index.js", "--http"]
