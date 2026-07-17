# AI Couples Therapy

## Product Requirements & Technical Specification

Version 1.0

---

# Project Overview

Build a production-quality Android application called **AI Couples Therapy**.

The application allows two people in a committed relationship to communicate with one another through a moderated AI therapist.

Unlike ChatGPT, where users interact directly with the AI, this application behaves more like a messaging application where:

Partner A sends messages.

Partner B sends messages.

The AI participates in the conversation as a licensed couples therapist.

The AI remembers prior therapy sessions through an intelligent long-term memory system.

The application should feel like using Google Messages or iMessage, except there are three participants:

Partner A

Partner B

AI Therapist

---

# Primary Goals

The application must:

Provide secure authentication

Pair two partners together

Provide a clean messaging interface

Allow AI-guided therapy sessions

Persist conversation history

Maintain long-term therapeutic memory

Scale efficiently without exploding token costs

---

# Technology Stack

Frontend

Android

Kotlin

Jetpack Compose

Material 3

MVVM Architecture

Coroutines

Flow

Navigation Compose

Dependency Injection (Hilt)

Backend

Supabase

Authentication

Google OAuth

Database

Postgres (Supabase)

Realtime Messaging

Supabase Realtime

Storage

Supabase Storage

AI

OpenAI ChatGPT API

Support configurable API Key

Future support:

Anthropic Claude

Gemini

Local LLM

---

# Authentication

Only Google Sign-In.

Workflow

User presses

Continue with Google

↓

Google OAuth

↓

Supabase Auth

↓

User Profile created

Fields

User ID

Google ID

Email

Display Name

Photo

Created Date

---

# Couple Pairing

Each user has:

Unique Pair Code

Example

ABF39Q

Partner enters code.

If accepted:

relationship_id created

Both users linked.

Only one partner allowed.

---

Database

Users

id

email

display_name

photo

relationship_id

Relationships

id

partner1

partner2

created_at

---

# Home Screen

Simple.

Large button

Start Therapy

History

Settings

---

# Therapy Session Workflow

Partner A

Presses

Start Therapy

↓

Supabase creates session

↓

Notification sent

↓

Partner B receives

"Chris wants to begin a therapy session."

Buttons

Join

Decline

If Join

Chat window opens.

---

# Chat UI

The interface should resemble:

Google Messages

or

iMessage

Three message colors

Partner A

Partner B

AI Therapist

Typing indicators

Read receipts

Timestamps

Auto-scroll

Emoji support

Image support (future)

Voice support (future)

---

Top Bar

Partner names

Session timer

Menu

End Session button

---

Bottom Bar

Text entry

Send button

Microphone placeholder

---

# AI Behavior

The AI acts only as a professional couples therapist.

Never chooses sides.

Encourages healthy communication.

Asks questions.

Reflects emotions.

Keeps discussions productive.

The AI should not dominate conversation.

It should wait until appropriate moments before responding.

---

# Session Lifecycle

Start

↓

Conversation

↓

End Button

or

10 minutes inactivity

↓

Confirm End

↓

Lock Session

↓

Generate AI Handoff

↓

Save

↓

Return Home

---

# Automatic Session Timeout

If no user sends a message for

10 minutes

Automatically end session.

Notify both users.

Generate summary.

---

# AI Memory System

This is the most important feature.

The AI should never begin a session with no memory.

Instead, it maintains a living therapeutic memory.

---

Memory Levels

Level 1

Current Session

Entire conversation

---

Level 2

Recent Memory

Rolling memory

Last five sessions

Stored as

AI Handoff Document

---

Level 3

Long-Term Memory

After five sessions:

Summarize those five sessions.

Archive original.

Create new rolling memory.

Repeat forever.

---

AI Handoff Document

The AI should generate structured JSON.

Example

```json
{
  "relationship_summary": "...",
  "major_conflicts": [],
  "communication_patterns": [],
  "wins": [],
  "goals": [],
  "follow_up_topics": [],
  "emotional_progress": "...",
  "next_session_focus": "...",
  "sessions_included": 5
}
```

Do NOT save plain paragraphs.

Store structured data.

This makes future prompts deterministic.

---

Beginning of Every Session

The system prompt becomes:

You are continuing therapy with this couple.

Review this therapeutic memory.

Maintain continuity.

Do not repeat previous interventions unless appropriate.

Then append:

Current Session Messages

---

Ending Every Session

Prompt AI

Generate updated therapeutic memory.

Include:

Progress

Recurring issues

Resolved conflicts

Homework

Communication improvements

Open concerns

Recommended next session

Return JSON only.

---

Memory Compression Algorithm

Sessions 1-5

↓

Rolling Memory

↓

Session 6

↓

Compress

↓

Archive Memory A

↓

Create Memory B

↓

Sessions 6-10

↓

Repeat forever.

---

Database Schema

Users

Relationships

Sessions

Messages

AI Memory

AI Archives

Notifications

Settings

---

Sessions

id

relationship_id

started_at

ended_at

duration

ended_by

status

---

Messages

id

session_id

sender

content

timestamp

tokens

model

---

AI Memory

relationship_id

version

memory_json

created

updated

---

Archives

relationship_id

archive_number

summary

created

---

Notifications

Push notifications

New session

Partner joined

Session ended

---

Realtime

Use Supabase Realtime.

Messages appear instantly.

---

Security

Row Level Security

Users only see:

Their own profile

Their own relationship

Their own sessions

Their own memories

Nothing else.

---

API Layer

Repository Pattern

AIRepository

SessionRepository

RelationshipRepository

AuthRepository

NotificationRepository

MemoryRepository

---

AI Service

Functions

Create Session Context

Send Message

Receive AI Response

Generate Memory

Compress Memory

Archive Memory

Load Memory

---

Prompt Engineering

System Prompt

Includes

Therapist instructions

Relationship memory

Current session metadata

Safety instructions

Conversation rules

---

Prompt Flow

Load Memory

↓

Inject Memory

↓

Append Conversation

↓

Generate Response

↓

Return Message

---

Performance

Use streaming responses.

Cache profile data.

Lazy load history.

Paginate messages.

Offline support.

---

Future Features

Voice therapy

Speech-to-text

Emotion detection

Relationship insights dashboard

Homework assignments

Weekly reports

Calendar scheduling

Export PDF

Shared journal

Counselor handoff

---

Development Roadmap

### Phase 1

Authentication

Supabase

Google Login

Pairing

### Phase 2

Messaging UI

Realtime messaging

Notifications

### Phase 3

AI integration

Streaming responses

Prompt orchestration

### Phase 4

Memory engine

Handoff generation

Compression

Archive

### Phase 5

History

Search

Settings

Dark mode

### Phase 6

Testing

Unit tests

Integration tests

UI tests

Security testing

Performance testing

---

## Suggested Project Structure

```
app/
 ├── auth/
 ├── pairing/
 ├── home/
 ├── therapy/
 │    ├── ui/
 │    ├── viewmodel/
 │    ├── repository/
 │    ├── ai/
 │    ├── prompts/
 │    ├── memory/
 │    └── notifications/
 ├── history/
 ├── settings/
 ├── data/
 ├── network/
 ├── database/
 └── common/
```

## Additional Recommendations

There are a few architectural improvements I would make before implementation:

1. **Separate "conversation history" from "therapeutic memory."** Don't rely only on summaries. Store the full transcript permanently (encrypted), but feed only the structured therapeutic memory plus a small amount of recent conversation to the AI. This preserves history while keeping token usage predictable.

2. **Use the OpenAI Responses API with conversation state where appropriate.** Instead of manually concatenating every prior message, use your structured handoff document as durable memory and the active session as working context. This reduces token consumption and simplifies prompt management.

3. **Store memory as structured JSON rather than prose.** Include fields such as recurring themes, strengths, unresolved issues, communication patterns, agreed commitments, and suggested next-session focus. Structured data is easier for the AI to consume consistently and is more resistant to hallucinations than free-form summaries.

4. **Introduce a prompt orchestration layer.** Every AI response should be built from:

   * A fixed therapist system prompt.
   * The current therapeutic memory JSON.
   * The last several messages from the current session.
   * Any application state (for example, "this is the first session" or "the session is ending; generate a handoff").

5. **Version every memory document.** Never overwrite without keeping history. Save each handoff with a version number and timestamp so you can audit, restore, or improve summarization logic later.
