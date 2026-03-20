import type { Metadata } from "next";
import "./globals.scss";

export const metadata: Metadata = {
  title: "Hermes Messenger",
  description: "Hermes Bridge web client on React and Next.js",
  manifest: "/manifest.webmanifest",
  icons: {
    icon: "/icons/hermes-icon.svg",
    apple: "/icons/apple-touch-icon.png",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ru">
      <body>{children}</body>
    </html>
  );
}
