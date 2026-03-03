import DayContent from "./components/DayContent";

const API_BASE = process.env.API_BASE_URL ?? "http://localhost:8080";

// ── Types ─────────────────────────────────────────────────────────────────────

interface IndexItem {
  id: string;
  bucket: "NEW" | "UPDATED";
  title: string;
  url: string;
  publishedAt: string;
  updatedAt?: string;
  format?: string;
  organisations: string[];
  source: "govuk" | "parliament" | "ons";
  sourceSubtype?: "bill" | "si" | "release";
  sourceReference?: string;
}

interface DailyIndexResponse {
  date: string;
  totalCount: number;
  newCount: number;
  updatedCount: number;
  items: IndexItem[];
}

// ── Date helpers ──────────────────────────────────────────────────────────────

function londonYesterday(): string {
  const londonToday = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Europe/London",
  }).format(new Date());
  return addDays(londonToday, -1);
}

function addDays(dateStr: string, n: number): string {
  const d = new Date(dateStr + "T12:00:00Z");
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}

function formatDisplayDate(dateStr: string): string {
  const d = new Date(dateStr + "T12:00:00Z");
  return d.toLocaleDateString("en-GB", {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  });
}

// ── Data fetching ─────────────────────────────────────────────────────────────

async function fetchDay(date: string): Promise<DailyIndexResponse | null> {
  const res = await fetch(`${API_BASE}/v1/days/${date}`, {
    next: { revalidate: 300 },
  });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`API error ${res.status} for ${date}`);
  return res.json();
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default async function Page({
  searchParams,
}: {
  searchParams: Promise<{ date?: string; source?: string }>;
}) {
  const params = await searchParams;
  const yesterday = londonYesterday();
  const date = params.date ?? yesterday;
  const source = params.source ?? "";
  const prevDate = addDays(date, -1);
  const nextDate = addDays(date, 1);
  const hasNext = nextDate < yesterday;

  const data = await fetchDay(date);

  return (
    <>
      <style>{globalCss}</style>

      <header style={styles.header}>
        <div>
          <h1 style={styles.headerH1}>Whitehall Yesterday</h1>
          <div style={styles.tagline}>Daily structured index of UK government &amp; Parliament publications</div>
        </div>
      </header>

      <nav style={styles.nav}>
        <a href={`/?date=${prevDate}`} style={styles.navLink}>
          &larr; {prevDate}
        </a>
        <span style={styles.dateLabel}>{formatDisplayDate(date)}</span>
        {hasNext && (
          <a href={`/?date=${nextDate}`} style={styles.navLink}>
            {nextDate} &rarr;
          </a>
        )}
      </nav>

      <DayContent data={data} date={date} source={source} />

      <footer style={styles.footer}>
        Data sourced from{" "}
        <a href="https://www.gov.uk" target="_blank" rel="noopener" style={{ color: "#1d70b8" }}>
          GOV.UK
        </a>
        {" · "}
        <a href="https://www.ons.gov.uk" target="_blank" rel="noopener" style={{ color: "#1d70b8" }}>
          ONS
        </a>
        {" · "}
        <a href="https://www.parliament.uk" target="_blank" rel="noopener" style={{ color: "#1d70b8" }}>
          UK Parliament
        </a>
      </footer>
    </>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────────

const globalCss = `
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: "GDS Transport", Arial, sans-serif; font-size: 16px; color: #0b0c0c; background: #f3f2f1; }
  a { color: #1d70b8; }
  a:hover { color: #003078; }
`;

const styles = {
  header: {
    background: "#0b0c0c",
    color: "#fff",
    padding: "12px 24px",
    display: "flex",
    alignItems: "center",
    gap: 16,
  } as React.CSSProperties,
  headerH1: { fontSize: 20, fontWeight: 700, letterSpacing: -0.5 } as React.CSSProperties,
  tagline: { fontSize: 13, color: "#b1b4b6" } as React.CSSProperties,

  nav: {
    background: "#fff",
    borderBottom: "4px solid #1d70b8",
    padding: "10px 24px",
    display: "flex",
    alignItems: "center",
    gap: 16,
  } as React.CSSProperties,
  navLink: { textDecoration: "none", fontSize: 14, fontWeight: 600, color: "#1d70b8" } as React.CSSProperties,
  dateLabel: { fontSize: 15, fontWeight: 700, flex: 1 } as React.CSSProperties,

  footer: { textAlign: "center", padding: "32px 0 16px", fontSize: 12, color: "#505a5f" } as React.CSSProperties,
};
