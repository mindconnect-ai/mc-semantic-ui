// @ts-check
import {themes as prismThemes} from 'prism-react-renderer';

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'semantic-ui',
  tagline: 'Fast, dynamic server-driven UI — SSR, SPA and a visual editor from one typed model',
  favicon: 'img/favicon.svg',

  // These docs live in this repo (mc-semantic-ui). If published, they go to
  // GitHub Pages for this repo; deployment is optional.
  url: 'https://mindconnect-ai.github.io',
  baseUrl: '/mc-semantic-ui/',

  organizationName: 'mindconnect-ai',
  projectName: 'mc-semantic-ui',

  onBrokenLinks: 'warn',

  i18n: {defaultLocale: 'en', locales: ['en']},

  markdown: {
    mermaid: true,
    hooks: {onBrokenMarkdownLinks: 'warn'},
  },
  themes: ['@docusaurus/theme-mermaid'],

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          sidebarPath: './sidebars.js',
          routeBasePath: '/',
          // No editUrl: the sources live in the private mc-monorepo, so an
          // "Edit this page" link would 404 for public visitors.
        },
        blog: false,
        theme: {customCss: './src/css/custom.css'},
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      image: 'img/semantic-ui/how-it-works.svg',
      colorMode: {defaultMode: 'light', respectPrefersColorScheme: true},
      navbar: {
        title: 'MindConnect',
        logo: {
          alt: 'MindConnect logo',
          src: 'img/logo.svg',
          srcDark: 'img/logo-dark.svg',
        },
        items: [
          {type: 'docSidebar', sidebarId: 'mainSidebar', position: 'left', label: 'Docs'},
          {
            href: 'https://github.com/mindconnect-ai/mc-semantic-ui',
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Docs',
            items: [
              {label: 'Semantic UI', to: '/'},
            ],
          },
          {
            title: 'Project',
            items: [
              {label: 'GitHub', href: 'https://github.com/mindconnect-ai/mc-semantic-ui'},
              {label: 'Discussions', href: 'https://github.com/mindconnect-ai/mc-semantic-ui/discussions'},
              {label: 'Support (Ko-fi)', href: 'https://ko-fi.com/beisdog'},
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} David Beisert. Apache-2.0.`,
      },
      prism: {
        theme: prismThemes.github,
        darkTheme: prismThemes.dracula,
        additionalLanguages: ['java', 'bash', 'json', 'yaml'],
      },
    }),
};

export default config;
