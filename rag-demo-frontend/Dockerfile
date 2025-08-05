FROM node:20 AS build
WORKDIR /app
COPY . .
RUN npm install
RUN npm run build --prod

FROM nginx:alpine
COPY --from=build /app/dist/angular-app /usr/share/nginx/html
EXPOSE 80
