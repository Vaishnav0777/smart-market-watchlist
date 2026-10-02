import type { NextConfig } from "next";

const configuredProxyTarget = process.env.API_PROXY_TARGET ?? "http://localhost:8080";
const apiProxyTarget = configuredProxyTarget.includes("://")
  ? configuredProxyTarget
  : `http://${configuredProxyTarget}`;

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${apiProxyTarget}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
