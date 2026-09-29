export default function Home() {
  return (
    <main className="flex flex-1 items-center">
      <div className="mx-auto w-full max-w-3xl px-6 py-24">
        <div className="mb-10 flex h-11 w-11 items-center justify-center rounded-full border border-brass/40">
          <span className="h-2.5 w-2.5 rounded-full bg-brass" />
        </div>
        <p className="text-xs font-medium tracking-[0.22em] text-brass uppercase">
          Market intelligence
        </p>
        <h1 className="mt-4 max-w-xl font-serif text-5xl leading-tight tracking-tight text-foreground sm:text-6xl">
          Smart Market Watchlist
        </h1>
        <p className="mt-6 max-w-xl text-xl leading-8 text-muted">
          Intelligent market monitoring and portfolio insights.
        </p>
        <p className="mt-12 max-w-lg border-t border-line pt-6 text-sm leading-6 text-muted">
          Development foundation. Market figures in this project are synthetic
          sample data.
        </p>
      </div>
    </main>
  );
}
