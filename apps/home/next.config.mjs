/** @type {import('next').NextConfig} */

const isProd = process.env.NODE_ENV === 'production'
const gateway = process.env.GATEWAY_URL || 'http://localhost:8080'

const nextConfig = {
  transpilePackages: ["@kb/shared"],
  reactStrictMode: true,
  ...(isProd ? { output: 'export' } : {}),
  trailingSlash: false,
  typescript: {},
  images: {
    unoptimized: true,
  },
  ...(!isProd
    ? {
        async rewrites() {
          return [{ source: '/api/:path*', destination: `${gateway}/api/:path*` }]
        },
      }
    : {}),
}

export default nextConfig
