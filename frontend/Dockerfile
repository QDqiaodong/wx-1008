ARG DOCKER_REGISTRY=
FROM ${DOCKER_REGISTRY}node:20-alpine AS build
WORKDIR /app
ENV LANG=C.UTF-8 LC_ALL=C.UTF-8
COPY package.json package-lock.json ./
RUN npm ci --registry=https://registry.npmmirror.com --fetch-retries=5 --fetch-retry-mintimeout=20000 --fetch-retry-maxtimeout=120000
COPY . .
RUN npm run build

FROM ${DOCKER_REGISTRY}nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
