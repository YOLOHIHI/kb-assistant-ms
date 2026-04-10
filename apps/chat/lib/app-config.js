import { appCopy } from "./copy"

export const frontendConfig = {
  brandName: appCopy.brandName,
  description: appCopy.appDescription,
  basePath: "/chat-app",
  dev: {
    host: "0.0.0.0",
    port: 3000,
    defaultGatewayUrl: "http://localhost:8080",
  },
}
