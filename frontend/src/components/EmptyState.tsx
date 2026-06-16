import { ShieldIcon } from './icons'

const EXAMPLE_QUESTIONS = [
  'Wie viele Urlaubstage habe ich pro Jahr?',
  'Wie lang müssen Passwörter sein?',
  'Wann wurde das Unternehmen gegründet?',
]

interface EmptyStateProps {
  onPick: (question: string) => void
}

export function EmptyState({ onPick }: EmptyStateProps) {
  return (
    <div className="flex h-full flex-col items-center justify-center px-4 py-10 text-center">
      <span className="mb-5 flex h-14 w-14 items-center justify-center rounded-2xl bg-accent/10 text-accent">
        <ShieldIcon width={28} height={28} />
      </span>
      <h2 className="text-xl font-semibold tracking-tight text-slate-900 dark:text-slate-100">
        Willkommen bei MittelstandGPT
      </h2>
      <p className="mt-2 max-w-md text-sm leading-relaxed text-slate-500 dark:text-slate-400">
        Stellen Sie Fragen zu Ihren Dokumenten. Die Antworten stammen aus einem lokalen
        KI-Modell – Ihre Daten verlassen diesen Server nicht.
      </p>

      <div className="mt-8 grid w-full max-w-md gap-2">
        <p className="text-left text-[11px] font-semibold uppercase tracking-wider text-slate-400 dark:text-slate-500">
          Beispielfragen
        </p>
        {EXAMPLE_QUESTIONS.map((question) => (
          <button
            key={question}
            type="button"
            onClick={() => onPick(question)}
            className="group flex items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3 text-left text-sm text-slate-700 shadow-sm transition hover:border-accent hover:shadow-md focus:outline-none focus-visible:ring-2 focus-visible:ring-accent dark:border-slate-800 dark:bg-slate-900 dark:text-slate-200"
          >
            <span>{question}</span>
            <span className="text-slate-300 transition group-hover:translate-x-0.5 group-hover:text-accent dark:text-slate-600">
              →
            </span>
          </button>
        ))}
      </div>
    </div>
  )
}
