import { Suspense } from "react";

const API_BASE = process.env.API_BASE_URL ?? "http://localhost:8080";

// ── Types ────────────────────────────────────────────────────────────────────

interface IndexItem {
  id: string;
  bucket: "NEW" | "UPDATED";
  title: string;
  url: string;
  publishedAt: string;
  updatedAt?: string;
  format?: string;
  organisations: string[];
  source: "govuk" | "parliament";
  sourceSubtype?: "bill" | "si";
  sourceReference?: string;
}

interface DailyIndexResponse {
  date: string;
  totalCount: number;
  newCount: number;
  updatedCount: number;
  items: IndexItem[];
}

// ── Date helpers ─────────────────────────────────────────────────────────────

function londonYesterday(): string {
  // en-CA gives unambiguous YYYY-MM-DD format
  const londonToday = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Europe/London",
  }).format(new Date());
  return addDays(londonToday, -1);
}

function addDays(dateStr: string, n: number): string {
  const d = new Date(dateStr + "T12:00:00Z"); // noon UTC avoids DST edge cases
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

// ── Components ────────────────────────────────────────────────────────────────

function ItemCard({ item }: { item: IndexItem }) {
  return (
    <li style={styles.item}>
      <div style={styles.itemTitle}>
        <a href={item.url} target="_blank" rel="noopener" style={styles.itemLink}>
          {item.title}
        </a>
      </div>
      <div style={styles.itemMeta}>
        {item.source === "parliament" && (
          <span style={styles.badgeParly}>
            {item.sourceSubtype === "bill" ? "BILL" : "SI"}
          </span>
        )}
        {item.format && (
          <span style={styles.formatTag}>{item.format.replace(/_/g, " ")}</span>
        )}
        {item.organisations.map((org) => (
          <span key={org} style={styles.org}>
            · {org.replace(/-/g, " ")}
          </span>
        ))}
      </div>
    </li>
  );
}

// ── Page ─────────────────────────────────────────────────────────────────────

export default async function Page({
  searchParams,
}: {
  searchParams: Promise<{ date?: string }>;
}) {
  const params = await searchParams;
  const yesterday = londonYesterday();
  const date = params.date ?? yesterday;
  const prevDate = addDays(date, -1);
  const nextDate = addDays(date, 1);
  const hasNext = nextDate < yesterday;

  const data = await fetchDay(date);

  const govukNew     = data?.items.filter((i) => i.source === "govuk"       && i.bucket === "NEW")     ?? [];
  const govukUpdated = data?.items.filter((i) => i.source === "govuk"       && i.bucket === "UPDATED") ?? [];
  const bills        = data?.items.filter((i) => i.sourceSubtype === "bill") ?? [];
  const sis          = data?.items.filter((i) => i.sourceSubtype === "si")   ?? [];

  const parliamentCount = bills.length + sis.length;

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

      <main style={styles.container}>
        {!data ? (
          <div style={styles.noData}>
            <strong>No data for {date}</strong>
            <p style={styles.noDataP}>
              Ingestion has not run for this date, or it failed. Try{" "}
              <a href="/" style={{ color: "#1d70b8" }}>yesterday</a> or trigger
              a manual ingest: <code>POST /v1/ingest/{date}</code>
            </p>
          </div>
        ) : (
          <>
            <div style={styles.summary}>
              <h2 style={styles.summaryH2}>Publications &amp; Parliament — {date}</h2>
              <div style={styles.counts}>
                <span>{data.totalCount} items total</span>
                <span style={{ marginLeft: 16 }}>{data.newCount} new</span>
                <span style={{ marginLeft: 16 }}>{data.updatedCount} updated</span>
                {parliamentCount > 0 && (
                  <span style={{ marginLeft: 16 }}>{parliamentCount} parliament</span>
                )}
              </div>
            </div>

            {govukNew.length > 0 && (
              <section style={styles.section}>
                <h2 style={styles.sectionTitle}>
                  <span style={styles.badgeNew}>NEW</span> Published yesterday
                </h2>
                <ul style={styles.itemList}>
                  {govukNew.map((item) => (
                    <ItemCard key={item.id} item={item} />
                  ))}
                </ul>
              </section>
            )}

            {bills.length > 0 && (
              <section style={styles.section}>
                <h2 style={styles.sectionTitle}>
                  <span style={styles.badgeParly}>BILL</span> Bills introduced
                </h2>
                <ul style={styles.itemList}>
                  {bills.map((item) => (
                    <ItemCard key={item.id} item={item} />
                  ))}
                </ul>
              </section>
            )}

            {sis.length > 0 && (
              <section style={styles.section}>
                <h2 style={styles.sectionTitle}>
                  <span style={styles.badgeParly}>SI</span> Statutory Instruments laid
                </h2>
                <ul style={styles.itemList}>
                  {sis.map((item) => (
                    <ItemCard key={item.id} item={item} />
                  ))}
                </ul>
              </section>
            )}

            {govukUpdated.length > 0 && (
              <section style={styles.section}>
                <h2 style={styles.sectionTitle}>
                  <span style={styles.badgeUpd}>UPDATED</span> Updated yesterday
                </h2>
                <ul style={styles.itemList}>
                  {govukUpdated.map((item) => (
                    <ItemCard key={item.id} item={item} />
                  ))}
                </ul>
              </section>
            )}
          </>
        )}
      </main>

      <footer style={styles.footer}>
        Data sourced from{" "}
        <a href="https://www.gov.uk" target="_blank" rel="noopener" style={{ color: "#1d70b8" }}>
          GOV.UK
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

  container: { maxWidth: 960, margin: "24px auto", padding: "0 24px" } as React.CSSProperties,

  noData: {
    background: "#fff",
    borderLeft: "6px solid #f47738",
    padding: "16px 20px",
    marginBottom: 24,
  } as React.CSSProperties,
  noDataP: { fontSize: 15, color: "#505a5f", marginTop: 6 } as React.CSSProperties,

  summary: {
    background: "#fff",
    borderLeft: "6px solid #1d70b8",
    padding: "16px 20px",
    marginBottom: 24,
  } as React.CSSProperties,
  summaryH2: { fontSize: 18, marginBottom: 4 } as React.CSSProperties,
  counts: { fontSize: 14, color: "#505a5f", marginTop: 6 } as React.CSSProperties,

  section: { marginBottom: 32 } as React.CSSProperties,
  sectionTitle: {
    fontSize: 20,
    fontWeight: 700,
    borderBottom: "2px solid #b1b4b6",
    paddingBottom: 8,
    marginBottom: 16,
    display: "flex",
    alignItems: "center",
    gap: 10,
  } as React.CSSProperties,
  badgeNew: {
    background: "#00703c",
    color: "#fff",
    fontSize: 11,
    fontWeight: 700,
    padding: "2px 8px",
    borderRadius: 2,
    letterSpacing: 0.5,
  } as React.CSSProperties,
  badgeUpd: {
    background: "#1d70b8",
    color: "#fff",
    fontSize: 11,
    fontWeight: 700,
    padding: "2px 8px",
    borderRadius: 2,
    letterSpacing: 0.5,
  } as React.CSSProperties,
  badgeParly: {
    background: "#5C2D91",
    color: "#fff",
    fontSize: 11,
    fontWeight: 700,
    padding: "2px 8px",
    borderRadius: 2,
    letterSpacing: 0.5,
  } as React.CSSProperties,

  itemList: { listStyle: "none" } as React.CSSProperties,
  item: {
    background: "#fff",
    border: "1px solid #b1b4b6",
    borderRadius: 2,
    padding: "14px 16px",
    marginBottom: 10,
  } as React.CSSProperties,
  itemTitle: {} as React.CSSProperties,
  itemLink: { fontSize: 16, fontWeight: 600, textDecoration: "none", color: "#1d70b8" } as React.CSSProperties,
  itemMeta: {
    fontSize: 13,
    color: "#505a5f",
    marginTop: 6,
    display: "flex",
    flexWrap: "wrap",
    gap: 6,
    alignItems: "center",
  } as React.CSSProperties,
  formatTag: {
    background: "#f3f2f1",
    border: "1px solid #b1b4b6",
    padding: "1px 7px",
    borderRadius: 2,
    fontSize: 12,
  } as React.CSSProperties,
  org: { fontSize: 12, color: "#505a5f" } as React.CSSProperties,

  footer: { textAlign: "center", padding: "32px 0 16px", fontSize: 12, color: "#505a5f" } as React.CSSProperties,
};
