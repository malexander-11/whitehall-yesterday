"use client";

import { useState } from "react";

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

// ── Item card ─────────────────────────────────────────────────────────────────

function ItemCard({ item }: { item: IndexItem }) {
  return (
    <li style={styles.item}>
      <div>
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
        {item.source === "ons" && <span style={styles.badgeOns}>ONS</span>}
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

// ── DayContent ────────────────────────────────────────────────────────────────

interface Props {
  data: DailyIndexResponse | null;
  date: string;
  source: string;
}

export default function DayContent({ data, date, source }: Props) {
  const [query, setQuery] = useState("");
  const [searchResults, setSearchResults] = useState<IndexItem[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isSearchActive = searchResults !== null;

  async function handleSearch() {
    if (query.trim().length < 3) return;
    setLoading(true);
    setError(null);
    try {
      const res = await fetch("/api/search", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ date, query: query.trim() }),
      });
      if (!res.ok) throw new Error(`Search returned ${res.status}`);
      const result: DailyIndexResponse = await res.json();
      setSearchResults(result.items);
    } catch {
      setError("Search unavailable — showing original results.");
      setSearchResults(null);
    } finally {
      setLoading(false);
    }
  }

  function handleClear() {
    setQuery("");
    setSearchResults(null);
    setError(null);
  }

  function handleKeyDown(e: React.KeyboardEvent) {
    if (e.key === "Enter") handleSearch();
  }

  // ── Source filter (shown only when not searching) ─────────────────────────

  const sourceFilter = !isSearchActive && (
    <div style={styles.sourceFilter}>
      {(["", "govuk", "ons", "parliament"] as const).map((s) => (
        <a
          key={s}
          href={`/?date=${date}${s ? `&source=${s}` : ""}`}
          style={source === s ? styles.sourceTabActive : styles.sourceTab}
        >
          {s === "" ? "All" : s === "govuk" ? "GOV.UK" : s === "ons" ? "ONS" : "Parliament"}
        </a>
      ))}
    </div>
  );

  // ── Item groups (when not searching) ──────────────────────────────────────

  const allItems = data?.items ?? [];
  const displayed = source && !isSearchActive
    ? allItems.filter((i) => i.source === source)
    : allItems;

  const govukNew     = displayed.filter((i) => i.source === "govuk" && i.bucket === "NEW");
  const govukUpdated = displayed.filter((i) => i.source === "govuk" && i.bucket === "UPDATED");
  const bills        = displayed.filter((i) => i.sourceSubtype === "bill");
  const sis          = displayed.filter((i) => i.sourceSubtype === "si");
  const onsItems     = displayed.filter((i) => i.source === "ons");

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <>
      {/* Search bar */}
      <div style={styles.searchBar}>
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Search today's publications…"
          style={styles.searchInput}
          aria-label="Search publications"
        />
        <button onClick={handleSearch} disabled={loading} style={styles.searchBtn}>
          {loading ? "Searching…" : "Search"}
        </button>
        {isSearchActive && (
          <button onClick={handleClear} style={styles.clearBtn}>
            Clear
          </button>
        )}
      </div>

      {error && <div style={styles.searchError}>{error}</div>}

      {/* Source filter tabs (hidden during search) */}
      {sourceFilter}

      {/* Results */}
      <main style={styles.container}>
        {!data ? (
          <div style={styles.noData}>
            <strong>No data for {date}</strong>
            <p style={styles.noDataP}>
              Ingestion has not run for this date, or it failed.
            </p>
          </div>
        ) : isSearchActive ? (
          <>
            <div style={styles.summary}>
              <h2 style={styles.summaryH2}>
                Results for &ldquo;{query}&rdquo;
              </h2>
              <div style={styles.counts}>
                {searchResults!.length === 0
                  ? "No results"
                  : `${searchResults!.length} results, ranked by relevance`}
              </div>
            </div>
            {searchResults!.length > 0 && (
              <ul style={styles.itemList}>
                {searchResults!.map((item) => (
                  <ItemCard key={item.id} item={item} />
                ))}
              </ul>
            )}
          </>
        ) : (
          <>
            <div style={styles.summary}>
              <h2 style={styles.summaryH2}>Publications &amp; Parliament — {date}</h2>
              <div style={styles.counts}>
                <span>{data.totalCount} items total</span>
                <span style={{ marginLeft: 16 }}>{data.newCount} new</span>
                <span style={{ marginLeft: 16 }}>{data.updatedCount} updated</span>
              </div>
            </div>

            {govukNew.length > 0 && (
              <section style={styles.section}>
                <h2 style={styles.sectionTitle}>
                  <span style={styles.badgeNew}>NEW</span> Published yesterday
                </h2>
                <ul style={styles.itemList}>
                  {govukNew.map((item) => <ItemCard key={item.id} item={item} />)}
                </ul>
              </section>
            )}

            {onsItems.length > 0 && (
              <section style={styles.section}>
                <h2 style={styles.sectionTitle}>
                  <span style={styles.badgeOns}>ONS</span> Statistical releases
                </h2>
                <ul style={styles.itemList}>
                  {onsItems.map((item) => <ItemCard key={item.id} item={item} />)}
                </ul>
              </section>
            )}

            {bills.length > 0 && (
              <section style={styles.section}>
                <h2 style={styles.sectionTitle}>
                  <span style={styles.badgeParly}>BILL</span> Bills introduced
                </h2>
                <ul style={styles.itemList}>
                  {bills.map((item) => <ItemCard key={item.id} item={item} />)}
                </ul>
              </section>
            )}

            {sis.length > 0 && (
              <section style={styles.section}>
                <h2 style={styles.sectionTitle}>
                  <span style={styles.badgeParly}>SI</span> Statutory Instruments laid
                </h2>
                <ul style={styles.itemList}>
                  {sis.map((item) => <ItemCard key={item.id} item={item} />)}
                </ul>
              </section>
            )}

            {govukUpdated.length > 0 && (
              <section style={styles.section}>
                <h2 style={styles.sectionTitle}>
                  <span style={styles.badgeUpd}>UPDATED</span> Updated yesterday
                </h2>
                <ul style={styles.itemList}>
                  {govukUpdated.map((item) => <ItemCard key={item.id} item={item} />)}
                </ul>
              </section>
            )}
          </>
        )}
      </main>
    </>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────────

const styles = {
  searchBar: {
    background: "#fff",
    borderBottom: "1px solid #b1b4b6",
    padding: "12px 24px",
    display: "flex",
    gap: 8,
    alignItems: "center",
  } as React.CSSProperties,
  searchInput: {
    flex: 1,
    maxWidth: 480,
    padding: "6px 10px",
    fontSize: 15,
    border: "2px solid #0b0c0c",
    borderRadius: 2,
    outline: "none",
  } as React.CSSProperties,
  searchBtn: {
    padding: "6px 16px",
    fontSize: 14,
    fontWeight: 700,
    background: "#1d70b8",
    color: "#fff",
    border: "none",
    borderRadius: 2,
    cursor: "pointer",
  } as React.CSSProperties,
  clearBtn: {
    padding: "6px 12px",
    fontSize: 14,
    background: "transparent",
    color: "#505a5f",
    border: "1px solid #b1b4b6",
    borderRadius: 2,
    cursor: "pointer",
  } as React.CSSProperties,
  searchError: {
    background: "#fef3f2",
    borderLeft: "4px solid #d4351c",
    padding: "8px 24px",
    fontSize: 14,
    color: "#d4351c",
  } as React.CSSProperties,

  sourceFilter: {
    background: "#f3f2f1",
    borderBottom: "1px solid #b1b4b6",
    padding: "8px 24px",
    display: "flex",
    gap: 4,
  } as React.CSSProperties,
  sourceTab: {
    padding: "4px 12px",
    border: "1px solid #b1b4b6",
    borderRadius: 2,
    fontSize: 13,
    color: "#1d70b8",
    textDecoration: "none",
    background: "#fff",
  } as React.CSSProperties,
  sourceTabActive: {
    padding: "4px 12px",
    border: "1px solid #0b0c0c",
    borderRadius: 2,
    fontSize: 13,
    color: "#fff",
    textDecoration: "none",
    background: "#0b0c0c",
  } as React.CSSProperties,

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
  badgeNew: { background: "#00703c", color: "#fff", fontSize: 11, fontWeight: 700, padding: "2px 8px", borderRadius: 2, letterSpacing: 0.5 } as React.CSSProperties,
  badgeUpd: { background: "#1d70b8", color: "#fff", fontSize: 11, fontWeight: 700, padding: "2px 8px", borderRadius: 2, letterSpacing: 0.5 } as React.CSSProperties,
  badgeParly: { background: "#5C2D91", color: "#fff", fontSize: 11, fontWeight: 700, padding: "2px 8px", borderRadius: 2, letterSpacing: 0.5 } as React.CSSProperties,
  badgeOns: { background: "#206095", color: "#fff", fontSize: 11, fontWeight: 700, padding: "2px 8px", borderRadius: 2, letterSpacing: 0.5 } as React.CSSProperties,

  itemList: { listStyle: "none" } as React.CSSProperties,
  item: {
    background: "#fff",
    border: "1px solid #b1b4b6",
    borderRadius: 2,
    padding: "14px 16px",
    marginBottom: 10,
  } as React.CSSProperties,
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
  formatTag: { background: "#f3f2f1", border: "1px solid #b1b4b6", padding: "1px 7px", borderRadius: 2, fontSize: 12 } as React.CSSProperties,
  org: { fontSize: 12, color: "#505a5f" } as React.CSSProperties,
};
