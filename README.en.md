# Finan+ for Android

**Português:** [README.md](README.md)

Finan+ is a personal finance app focused on simplicity, privacy, a light footprint and working offline.

This is the native Android version, written in **Kotlin + Jetpack Compose**: data encrypted on the device, balance widget, due-date notifications, PIN and fingerprint unlock, an assistant that works without internet, PDF reports, backups compatible with the other versions and **local network access** (use Finan+ in a computer's browser while the data stays on the phone).

**Download:** from [F-Droid](https://f-droid.org/packages/com.finanplus/) or the [signed APK on GitHub](../../releases/latest) · **Other versions:** [Finan+ web](https://finanplus-web.github.io/finan_plus/) ([source](https://github.com/finanplus-web/finan_plus)) · [Finan+ for Linux](https://github.com/finanplus-web/finan_plus_linux/releases/latest) ([source](https://github.com/finanplus-web/finan_plus_linux))

The app itself is in Brazilian Portuguese and uses the Brazilian real (R$). The detailed feature documents are in Portuguese.

## What's new in 1.13.0

- **Transfer between accounts:** a new "Transferir entre contas" option in the + button and in My money › Accounts and cards. The amount leaves one account and enters the other on the same day, without counting as income or expense (it stays out of the Summary, limits, reports and the assistant). The list shows both sides in a neutral color; editing or deleting one side changes both. Backup, CSV and network access keep the transfer. Details: [TRANSFERENCIAS.md](TRANSFERENCIAS.md).

## What's new in 1.12.0

- **New icons:** every icon in the app is now from Phosphor Icons (MIT license), lighter and rounder: Regular strokes in lists and buttons, the filled version on the active bottom tab and Duotone for icons inside squares (categories, My money, Settings). "To receive" and "To pay" use arrows in a circle. Only the drawings changed. Details: [ICONES.md](ICONES.md).
- **Network access with Finan+ web 1.5.0:** the computer's browser shows the same look and rules as the app, including goal reserves and shared expenses.
- No calculation rule or saved data changed. Full list in [CHANGELOG.md](CHANGELOG.md).

## What's new in 1.11.0

- **One number, one name, one rule:** Home, Transactions (list and calendar), Summary and the widget share the same calculation for To receive, To pay, Reserved for goals and Forecast. Money already saved in a goal is no longer subtracted twice, and overdue items show up in "To pay".
- **My money** with a summary at the top, an alert when a goal will miss its deadline, and dedicated screens for Accounts and cards, Debts (new), Limits, Goals and Categories.
- **New expense** with category and date chips, a collapsed "More options" with a summary, and "Undo" after saving.
- **Categories** on their own screen (drag to reorder, swipe to archive). Credit card and overdraft are no longer categories; Loan becomes Debts.
- Regrouped **Settings**, redesigned **Shared expense** and **What if…?**; correct plurals, one icon per concept and dedicated colors for goals (amber) and cards (lilac). Details: [REDESIGN-COERENCIA.md](REDESIGN-COERENCIA.md).

## What's new in 1.10.0

- **Home:** current balance and end-of-month forecast in a single card (an "info" button explains the forecast), "Still to receive" and "Still to pay" with the next amount, one Assistant warning when the balance does not cover the upcoming bills, and upcoming due dates grouped by day. Details: [INICIO.md](INICIO.md).
- **Summary:** month badge, result with the forecast balance and the amount saved in goals, comparison only when the previous month has data, an "In" / "Out or on the card" flow, up to 5 categories plus "Others", and history only for months with data. Details: [RESUMO.md](RESUMO.md).
- **New transaction screen:** full-screen Expense / Income form with a large amount formatted as you type, "Pay with" / "Receive in", the native date picker, a category drawer with search and recent picks, description suggestions and a fixed "Save" button with a confirmation. Details: [LANCAMENTO.md](LANCAMENTO.md).
- **My money:** overview (In accounts, In goals, Open bills), tappable accounts and cards, the bill inside each card with limit usage and alerts, a Planning card with goals and monthly limits, and collapsible Accounts / Cards sections that remember their state. Details: [MEU-DINHEIRO.md](MEU-DINHEIRO.md).
- No calculation rule and no saved data changed. Full list: [CHANGELOG.md](CHANGELOG.md).

## Features

| Area | What it does |
|---|---|
| Navigation | Bottom bar with Home, Transactions, My money, Summary and Settings; swipe sideways to move between tabs. The **+** button adds an expense, income, recurrence or shared expense |
| Home | Balance and forecast, what is still to receive and pay, Assistant warning, upcoming due dates, limits and goals |
| Transactions | **List** by month with custom period, search, quick filters, daily balance and mark as paid. **Calendar** with the balance of each day, bills on their due date and upcoming recurrences shown as "Forecast" |
| My money | Accounts, credit cards with the current bill and limit usage, card details with purchases and bill history, goals (saving into a goal takes money out of the account without counting as an expense), monthly limits per category, categories, shared expenses and the "What if…?" simulator |
| Summary | Result, income and expenses, money flow, expenses by category and monthly history, plus PDF export |
| Bill reader | Amount and due date from a PDF bill (including password-protected ones), a boleto line or a Pix copy-and-paste code, all on the device |
| Shared expenses | Split a bill equally or by amount, see who owes what, record partial payments, attach the receipt and share a PDF or image |
| Assistant | On the device, no internet: category suggestion, month summary, tips and quick questions, each with a "Why?" |
| Settings | 7 themes (System, Light, Gruvbox, OLED Gray, Gruvbox OLED, Tokyo Night, Nord) with optional glass effect, PIN, biometrics, hide values, auto-lock, screenshot blocking, notifications, widget, CSV, backup, encrypted **automatic backup** to a folder, restore |
| Network access | Optional: the phone serves Finan+ web to a browser on the same Wi-Fi over HTTPS, with a 6-digit code and approval on the phone; data never leaves the phone |
| Widget and notifications | Balance, forecast and next due date; a daily reminder of overdue and upcoming bills, discreet when the app is protected |

## Privacy and security

- Data encrypted with AES-256-GCM, key kept in the Android Keystore.
- PIN and/or strong biometrics, automatic lock, screenshots blocked by default and "hide values".
- No ads, no trackers, no account. The network permission is used only for the optional local network access.

## Building and testing

Open the project in a recent stable **Android Studio** and press **Run**. Requirements: Android 8.0 (API 26) or newer; target SDK 35.

```bash
./gradlew test   # business rules (JUnit)
```

GitHub Actions builds and tests every push to `main`, every pull request and every tag. The "Capturas de tela" workflow renders the screens with Robolectric.

## License

Finan+ is free software under the **GNU GPL v3 or later**. Third-party components and their licenses: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
