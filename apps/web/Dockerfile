# Obraz frontu — statyki zbudowane Vite i podane przez nginx. To osobna
# aplikacja od serwisu: własny obraz, własny cykl życia, własny port.
# nginx przekazuje /api na backend, więc front woła adresy względne i nie
# trzeba otwierać CORS-a w aplikacji bez auth (D2/D14).
#
# Kontekstem builda jest KATALOG GŁÓWNY repozytorium: front zależy od
# @archon/api-client z libs/ts, więc sam katalog apps/web nie wystarczy.

FROM node:22-alpine AS build
RUN corepack enable
WORKDIR /repo

# Manifesty przed kodem: warstwa z node_modules przeżywa zmianę źródeł.
COPY pnpm-workspace.yaml pnpm-lock.yaml package.json ./
COPY apps/web/package.json apps/web/
COPY libs/ts/api-client/package.json libs/ts/api-client/
RUN pnpm install --frozen-lockfile --filter web...

COPY libs/ts/api-client libs/ts/api-client
COPY apps/web apps/web
RUN pnpm --filter web build

FROM nginx:1.27-alpine AS runtime
# entrypoint obrazu nginx renderuje szablony z /etc/nginx/templates przez envsubst
ENV API_HOST=music-service
ENV API_PORT=8080
COPY apps/web/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /repo/apps/web/dist /usr/share/nginx/html
EXPOSE 80
