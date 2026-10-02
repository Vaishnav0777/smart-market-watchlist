import { apiRequest } from "@/lib/api/client";
import type { AssistantAnswer } from "@/lib/types";

export function askAssistant(question: string, portfolioId?: string | null): Promise<AssistantAnswer> {
  return apiRequest<AssistantAnswer>("/api/v1/assistant/questions", {
    method: "POST",
    body: { question, portfolioId: portfolioId ?? null },
  });
}
