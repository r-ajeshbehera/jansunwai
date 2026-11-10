import { useEffect, useState } from 'react';
import { api, errorMessage } from '../../api.js';
import { ErrorBox, Loading } from '../../components/ui.jsx';

export default function AiUsage() {
  const [usage, setUsage] = useState(null);
  const [provider, setProvider] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    api.get('/ai/usage').then((r) => setUsage(r.data)).catch((e) => setError(errorMessage(e)));
    api.get('/ai/provider').then((r) => setProvider(r.data)).catch(() => {});
  }, []);
  return (
    <div className="stack">
      <h1>AI usage</h1>
      <ErrorBox error={error} />
      {provider && <div className="card">
        <dl className="kv"><dt>Provider</dt><dd>{provider.provider} {provider.mock && <span className="badge b-amber">offline mock, no API cost</span>}</dd>
          <dt>Chat model</dt><dd className="mono">{provider.chatModel}</dd><dt>Embedding model</dt><dd className="mono">{provider.embeddingModel}</dd></dl>
        <p className="small muted">Switch with APP_AI_PROVIDER=spring-ai and AI_BASE_URL / AI_API_KEY / AI_CHAT_MODEL (Gemini free tier or local Ollama). See docs/AI_DESIGN.md.</p></div>}
      <div className="card">{!usage ? <Loading /> : (
        <table><thead><tr><th>Provider</th><th>Model</th><th>Calls</th><th>Failures</th><th>Input tokens</th><th>Output tokens</th><th>Est. cost (USD)</th><th>Avg latency</th></tr></thead>
          <tbody>{usage.map((u, i) => <tr key={i}><td>{u.provider}</td><td className="mono small">{u.model}</td><td>{u.calls}</td>
            <td>{u.failures > 0 ? <span className="badge b-red">{u.failures}</span> : 0}</td><td>{u.inputTokens}</td><td>{u.outputTokens}</td>
            <td>{Number(u.costUsd).toFixed(4)}</td><td>{Math.round(u.avgLatencyMs)} ms</td></tr>)}</tbody></table>)}
        <p className="small muted">Costs are estimates from the configured price per 1,000 tokens (0 for mock and local Ollama).</p></div>
    </div>
  );
}
