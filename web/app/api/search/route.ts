import { NextRequest, NextResponse } from "next/server";

const API_BASE = process.env.API_BASE_URL ?? "http://localhost:8080";

export async function POST(req: NextRequest) {
  const { date, query, limit = 50, offset = 0 } = await req.json();

  const res = await fetch(`${API_BASE}/v1/days/${date}/search`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ query, limit, offset }),
  });

  if (!res.ok) {
    return NextResponse.json({ error: "Search failed" }, { status: res.status });
  }

  return NextResponse.json(await res.json());
}
