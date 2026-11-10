import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the backend runs on :8080; Vite forwards /api so the browser sees one origin (no CORS issues).
export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: { manualChunks: { react: ['react', 'react-dom', 'react-router-dom'], charts: ['recharts'], maps: ['leaflet', 'react-leaflet'] } },
    },
  },
  server: { port: 5173, proxy: { '/api': 'http://localhost:8080' } },
});
