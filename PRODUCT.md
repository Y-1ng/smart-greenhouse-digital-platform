# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

The primary users are the operating team of a cold-arid region facility agriculture demonstration base and the institutions collaborating around it:

- **Base managers and production operators** monitor greenhouse environments, control ventilation, water and fertilizer, lighting, and other equipment, and handle alerts during daily production.
- **Quality and traceability staff** manage agricultural records, inspection reports, certificates, one-item-one-code identities, and blockchain evidence.
- **Cooperative and village administrators** track land, labor, capital, equity, dividends, contracts, orders, and village-level participation.
- **Government and regulatory users** monitor industrial scale, village participation, employment, collective income, project progress, risks, and external coordination.
- **Consumers** scan a product code to understand origin, production environment, inspection evidence, certificate status, and circulation history.

The interface must support both control-room use on wide screens and inspection or field use on phones.

## Product Purpose

The platform is a full-chain digital management system for a cold-arid region smart greenhouse demonstration base. It connects greenhouse IoT monitoring, production control, water and fertilizer management, crop records, quality inspection, one-item-one-code traceability, blockchain evidence, cooperative operations, and government governance in one operating system.

Success means that a base operator can understand the current greenhouse state and act quickly; quality staff can prove what happened; cooperative and government users can see how the shared operating model performs; and consumers can verify product origin and quality without needing internal system knowledge.

## Positioning

The product combines two mechanisms that are not interchangeable with a generic greenhouse dashboard:

1. **Production truth is carried from sensor to consumer evidence.** Live environmental data, agricultural operations, inspection documents, certificates, scan records, and hash evidence are represented as one traceable chain rather than isolated modules.
2. **The operating model is multi-village and shared.** The platform explicitly represents the "government leads, one village contributes land, multiple villages invest, cooperative operation" model through village participation, equity, dividends, contracts, and orders.

The cold-arid regional context is also a durable constraint: frost, sandstorms, strong wind, drought, low light, and heat-retention conditions are first-class operating concerns.

## Operating Context

- Base staff use the system in a production control room, at a workstation, or on a mobile device during inspection.
- Environmental and device data update continuously; users need current values, trends, thresholds, device state, and command feedback without navigating through unrelated modules.
- Quality evidence is assembled after field operations and laboratory inspection; the system must keep document state, certificate state, blockchain receipt state, and original data distinguishable.
- Cooperative and government users review periodic operating results rather than controlling equipment.
- Consumers use a public-facing traceability experience after scanning a code; it must not expose internal controls or administrative data.
- The current implementation is a graduation-design demonstration. Some external platform adapters, live chain services, and production-grade integrations may be represented by adapters, simulated modes, or documented boundaries rather than real third-party production connections.

## Capabilities and Constraints

### Confirmed target capability map

- **Production command:** park overview, single-greenhouse digital twin, environment monitoring, cold-arid risk alerts, intelligent control, water and fertilizer integration, crop growth records, and equipment maintenance.
- **Quality traceability:** agricultural operation records, inspection and certificate management, one-item-one-code lifecycle, blockchain evidence visualization, and a consumer-facing traceability view.
- **Cooperative operations:** village-level assets and participation, equity and dividends, contracts and meetings, orders and channels, and operating analysis.
- **Governance decisions:** government dashboard, cold-chain and circulation, energy and carbon efficiency, AI decision explanations, and role-specific mobile entry points.

### Current implementation constraints and honest boundaries

- The frontend is an existing Vue 3 application and must preserve working functionality while presentation evolves.
- The base currently demonstrates multiple greenhouse sites and a bounded set of devices, sensors, and crop batches. The interface must label sample, simulated, or fallback data honestly.
- Blockchain evidence must distinguish real chain mode, REST adapter mode, simulated mode, and pending compensation. A visible hash or receipt must not imply a real chain transaction when the mode is simulated or the adapter is unavailable.
- External platform integration is an adapter boundary unless a real production connection is explicitly configured.
- The platform is Chinese-first. Product terminology must stay consistent across production, quality, cooperation, and governance surfaces.
- High-stakes actions such as issuing certificates, compensation, device commands, and destructive edits need clear state feedback and recovery paths.

### Open decisions

- [To confirm] Which reference capabilities are required for the current graduation deliverable versus future roadmap: CO2 control, cold-chain visualization, energy/carbon dashboard, full consumer public page, and real external platform connections.
- [To confirm] Whether the consortium chain should be demonstrated with the current simulated or REST adapter mode during evaluation, or connected to a specific external node.

## Brand Commitments

- The product name is the **智慧温室大棚农产品全链条数字化管理平台**.
- The operating site context is a **寒旱区设施农业示范基地**.
- The platform voice is factual, operational, and restrained. It should not claim production proof, customer outcomes, or chain finality that the system cannot demonstrate.
- The product should read as one coherent system across production, quality, cooperation, and governance, rather than four unrelated dashboards.

## Evidence on Hand

- Existing Vue frontend source: `platform/前端应用_frontend/src`.
- Project design documentation: `docs/01_毕业设计项目总体设计文档_完善版.docx`.
- Existing implementation includes production environment monitoring, a Three.js greenhouse digital twin, device controls, water and fertilizer plans, cycle evidence, traceability query and evidence views, cooperative operation views, governance views, and an AI summary drawer.
- Existing screenshots and browser verification artifacts are under `output/playwright/`.
- No real customer testimonials, commercial performance claims, or production deployment evidence are available and must not be fabricated.

## Product Principles

1. **Operational truth before visual theater.** A status, number, command result, or chain receipt must be traceable to a real or clearly labeled simulated source.
2. **One event, many audiences.** The same greenhouse event should be representable in production, quality, cooperative, and governance contexts without becoming contradictory copies.
3. **Actionable density.** Control-room screens can be dense, but every visible metric must help a user decide, verify, or act.
4. **Evidence is a lifecycle.** Quality claims are not a single badge; inspection, certificate, trace code, scan, hash, and original data must remain distinguishable.
5. **The shared operating model is part of the product.** Village participation, equity, dividends, and collective benefit are not decorative reporting; they explain why the production system exists.

## Accessibility & Inclusion

- Target WCAG AA for web surfaces.
- Maintain keyboard access, visible focus, semantic labels on icon-only controls, and meaningful state announcements.
- Support desktop and mobile web layouts, including touch targets of at least 44 CSS pixels where practical.
- Respect `prefers-reduced-motion` for Three.js rotation, scene transitions, and dashboard microinteractions.
- Do not rely on color alone to communicate device, traceability, risk, or approval state.
