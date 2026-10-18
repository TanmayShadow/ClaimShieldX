# Builds the app, then serves it with the small Node server in ./server, which also forwards /api to the backend.
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:24-alpine
ENV NODE_ENV=production
WORKDIR /app
COPY --from=build /app/dist ./dist
COPY server/server.mjs ./server/server.mjs
USER node
# Cloud Run sets PORT. BACKEND_URL is required and is set at deploy time, not baked into the image.
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1:${PORT:-8080}/healthz || exit 1
CMD ["node", "server/server.mjs"]
