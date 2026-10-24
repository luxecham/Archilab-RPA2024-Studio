/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        btp: {
          dark: '#0F172A',
          blue: '#1E3A8A',
          primary: '#2563EB',
          amber: '#F59E0B',
          amberDark: '#D97706',
          emerald: '#10B981',
          danger: '#EF4444',
          slate: '#334155',
          light: '#F8FAFC'
        }
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['Fira Code', 'monospace']
      }
    },
  },
  plugins: [],
}
