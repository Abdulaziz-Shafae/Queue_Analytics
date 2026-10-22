import { defineConfig } from 'vite';
export default defineConfig({ server: { allowedHosts: ['terminal.local'], proxy: { '/api': 'http://localhost:8080', '/actuator': 'http://localhost:8080' } } });
