/** @type {import('next').NextConfig} */
const nextConfig = {
  // The floating dev-tools badge sits over the bottom-left of the layout and
  // reads as part of the product in a demo. Off in every environment.
  devIndicators: false,
  images: {
    remotePatterns: [
      {
        protocol: 'https',
        hostname: '**',
      },
    ],
  },
}

export default nextConfig
