# syntax=docker/dockerfile:1

FROM node:24.21.0-alpine3.24 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# Public Keycloak URL as seen by the browser (baked into the bundle by Vite).
ARG VITE_KEYCLOAK_URL=http://localhost:8081
ENV VITE_KEYCLOAK_URL=${VITE_KEYCLOAK_URL}
RUN npm run build

FROM nginx:1.30.5-alpine3.24
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
