"use client";

import { askAssistant } from "@/lib/api/assistant";
import { ErrorState, LoadingState } from "@/components/states";
import type { AssistantAnswer, AssistantSource } from "@/lib/types";
import { useState } from "react";

const examples = [
  "Which investment has performed best?",
  "What is my total P&L?",
  "How is my portfolio distributed across sectors?",
  "What changed since I last checked?",
];

const sourceLabel: Record<AssistantSource, string> = {
  portfolio_analytics: "Portfolio analytics",
  watchlist_changes: "Watchlist changes",
  portfolio_summary: "Portfolio summary",
};

export default function AssistantPage() {
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState<AssistantAnswer | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [pending, setPending] = useState(false);

  async function onAsk(event: React.FormEvent) {
    event.preventDefault();
    const trimmed = question.trim();
    if (!trimmed) {
      return;
    }
    setPending(true);
    setError(null);
    setAnswer(null);
    try {
      setAnswer(await askAssistant(trimmed));
    } catch (caught) {
      setError(caught);
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="grid max-w-3xl gap-6">
      <header>
        <p className="text-xs font-medium tracking-[0.18em] text-brass uppercase">Assistant</p>
        <h1 className="mt-2 font-serif text-4xl">Ask about your holdings</h1>
        <p className="mt-2 text-sm leading-6 text-muted">
          Answers come from your stored portfolio and watchlist figures. This assistant does not predict prices or recommend buying or selling.
        </p>
      </header>
      <form className="surface grid gap-3 px-4 py-4" onSubmit={onAsk}>
        <label className="grid gap-1 text-sm">
          Question
          <textarea
            className="field min-h-[6rem]"
            value={question}
            maxLength={500}
            onChange={(event) => setQuestion(event.target.value)}
          />
        </label>
        <div className="flex flex-wrap gap-2">
          {examples.map((example) => (
            <button
              key={example}
              className="button-secondary"
              type="button"
              onClick={() => setQuestion(example)}
            >
              {example}
            </button>
          ))}
        </div>
        <button className="button-primary w-fit" type="submit" disabled={pending || question.trim().length === 0} aria-busy={pending}>
          {pending ? "Checking your figures" : "Ask"}
        </button>
      </form>
      {pending ? <LoadingState label="Checking your figures" /> : null}
      {error ? <ErrorState error={error} onRetry={() => setError(null)} /> : null}
      {answer ? <AnswerCard answer={answer} /> : null}
    </div>
  );
}

function AnswerCard({ answer }: { answer: AssistantAnswer }) {
  return (
    <section className="surface px-5 py-5" aria-live="polite">
      <h2 className="font-serif text-2xl">{answer.refused ? "Declined" : "Answer"}</h2>
      <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-foreground">{answer.answer}</p>
      {answer.sources.length > 0 ? (
        <ul className="mt-4 flex flex-wrap gap-2">
          {answer.sources.map((source) => (
            <li key={source} className="rounded-full border border-line px-2 py-1 text-xs text-muted">
              {sourceLabel[source] ?? source}
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}
