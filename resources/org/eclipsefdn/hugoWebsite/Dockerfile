ARG BUILDER_IMAGE_TAG=latest
ARG NGINX_IMAGE_TAG=stable-alpine-for-hugo

FROM eclipsefdn/hugo-node:${BUILDER_IMAGE_TAG} AS builder
# See https://github.com/EclipseFdn/dockerfiles/tree/master/hugo-node
# and https://github.com/EclipseFdn/dockerfiles/blob/master/build.sh
# for hugo and node versions (note the latest in the build.sh file)

WORKDIR /workdir
COPY . /workdir/

RUN if [ -e yarn.lock ]; then yarn install --frozen-lockfile; elif [ -e package-lock.json ]; then npm --unsafe-perm ci; fi; \
    hugo --gc --minify

FROM eclipsefdn/nginx:${NGINX_IMAGE_TAG}

COPY --from=builder /workgitdir/public/ /usr/share/nginx/html/
