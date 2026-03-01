import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Whitehall Yesterday",
  description: "Daily structured index of UK government publications",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
