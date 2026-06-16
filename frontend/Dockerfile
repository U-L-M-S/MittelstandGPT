# Development image: runs the Vite dev server with hot reload.
# Source is bind-mounted in docker-compose.yml; node_modules lives in the image.
FROM node:22-alpine
WORKDIR /app

# Install dependencies (cached unless package.json changes)
COPY package.json ./
RUN npm install

# Copy the rest (overridden by the bind mount at runtime)
COPY . .

EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]
