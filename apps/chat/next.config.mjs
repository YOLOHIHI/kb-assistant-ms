/** @type {import('next').NextConfig} */

// next dev  → NODE_ENV = 'development' → dev server at :3000, proxies /api to Spring Boot
// next build → NODE_ENV = 'production'  → static export with basePath /chat-app

const isProd = process.env.NODE_ENV === 'production'
const gateway = process.env.GATEWAY_URL || 'http://localhost:8080'

const nextConfig = {
  transpilePackages: ["@kb/shared"],
  reactStrictMode: true,
  ...(isProd
    ? { output: 'export', basePath: '/chat-app' }
    : {}),

  trailingSlash: false,
  typescript: {},
  images:      { unoptimized: true },

  // Dev-only: proxy /api/* → Spring Boot gateway
  ...(!isProd
    ? {
        async rewrites() {
          return [{ source: '/api/:path*', destination: `${gateway}/api/:path*` }]
        },
      }
    : {}),
}

export default nextConfig
