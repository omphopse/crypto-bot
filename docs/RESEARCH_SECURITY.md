# Research Security & Secure Browser Abstraction

## 1. SSRF & URL Validation
- Strict HTTPS enforcement (`https://` required; `http://`, `file://`, `javascript://`, `data://` rejected).
- Rejection of private IP ranges (RFC 1918: `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`).
- Rejection of loopback addresses (`127.0.0.0/8`, `::1`, `localhost`).
- Rejection of cloud metadata services (`169.254.169.254`, `metadata.google.internal`).
- Validation of every redirect in the chain (`followRedirects = NEVER` with manual re-validation).

## 2. Source Policy & Whitelisting
- Configurable source policy modes: `ALLOWLIST`, `BLOCKLIST`, `UNRESTRICTED_WITH_SECURITY_FILTER`.
- Default allowlist includes official regulatory and reputable financial news domains (`sec.gov`, `bloomberg.com`, `reuters.com`, `federalreserve.gov`).

## 3. Cost & Rate Controls
- Rate limiting: max 30 requests per minute per node.
- Maximum page size: 512 KB per HTTP fetch.
- Maximum document length: 100,000 characters post-sanitization.

## 4. Evidence Traceability & Provenance
- Every piece of extracted evidence links to:
  - Source domain
  - Original URL
  - SHA-256 content hash
  - Ingestion timestamp
  - Security status (`CLEAN`, `SUSPICIOUS`, `BLOCKED`)
