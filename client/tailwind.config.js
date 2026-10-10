/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Manrope', 'system-ui', '-apple-system', 'BlinkMacSystemFont', '"Segoe UI"', 'Arial', 'sans-serif'],
      },
      colors: {
        // Keep existing utility names while sharing the public website palette.
        slate: {
          50: '#f4f7f8', 100: '#eaf0f2', 200: '#e3e8eb', 300: '#cad5db',
          400: '#8a9ba5', 500: '#64737d', 600: '#4b6575', 700: '#2d4d60',
          800: '#1f4155', 900: '#133449', 950: '#0c2535',
        },
        blue: {
          50: '#eef8f5', 100: '#ddf0ea', 200: '#bfe4d9', 300: '#9bd8c8',
          400: '#81cdbd', 500: '#479e89', 600: '#287864', 700: '#1b5e4e',
          800: '#164b40', 900: '#133449', 950: '#0c2535',
        },
        indigo: {
          50: '#eef8f5', 100: '#ddf0ea', 200: '#bfe4d9', 300: '#9bd8c8',
          400: '#81cdbd', 500: '#479e89', 600: '#287864', 700: '#1b5e4e',
          800: '#164b40', 900: '#133449', 950: '#0c2535',
        },
      },
      zIndex: {
        navigation: '40',
        modal: '50',
        toast: '60',
      },
    },
  },
  plugins: [],
};
