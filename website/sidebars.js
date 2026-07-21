// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  mainSidebar: [
    {
      type: 'category',
      label: 'Semantic UI',
      link: {type: 'doc', id: 'semantic-ui/overview'},
      items: [
        'semantic-ui/how-it-works',
        'semantic-ui/how-it-compares',
        {
          type: 'category',
          label: 'Core concepts',
          items: [
            'semantic-ui/node-vocabulary',
            'semantic-ui/runtime',
            'semantic-ui/triggers',
            'semantic-ui/triggers-cookbook',
            'semantic-ui/forms',
            'semantic-ui/responsive',
            'semantic-ui/overflow',
            'semantic-ui/feedback',
            'semantic-ui/icons',
            'semantic-ui/rendering-modes',
          ],
        },
        {
          type: 'category',
          label: 'Elements reference',
          link: {type: 'doc', id: 'semantic-ui/node-vocabulary'},
          items: [
            {
              type: 'category',
              label: 'Layout & structure',
              items: [
                'semantic-ui/elements/app-shell',
                'semantic-ui/elements/stack',
                'semantic-ui/elements/section',
                'semantic-ui/elements/section-entry',
                'semantic-ui/elements/page',
                'semantic-ui/elements/header',
              ],
            },
            {
              type: 'category',
              label: 'Data display',
              items: [
                'semantic-ui/elements/table',
                'semantic-ui/elements/column',
                'semantic-ui/elements/row',
                'semantic-ui/elements/list',
                'semantic-ui/elements/detail',
                'semantic-ui/elements/tree',
                'semantic-ui/elements/tree-node',
                'semantic-ui/elements/text',
              ],
            },
            {
              type: 'category',
              label: 'Input',
              items: [
                'semantic-ui/elements/form',
                'semantic-ui/elements/field',
                'semantic-ui/elements/fieldgroup',
                'semantic-ui/elements/upload',
              ],
            },
            {
              type: 'category',
              label: 'Actions & navigation',
              items: [
                'semantic-ui/elements/action',
                'semantic-ui/elements/link',
                'semantic-ui/elements/menu',
                'semantic-ui/elements/menu-item',
                'semantic-ui/elements/menu-button',
              ],
            },
            {
              type: 'category',
              label: 'Overlays & feedback',
              items: [
                'semantic-ui/elements/dialog',
                'semantic-ui/elements/toast',
                'semantic-ui/elements/spinner',
                'semantic-ui/elements/progress',
                'semantic-ui/elements/icon',
              ],
            },
          ],
        },
        {
          type: 'category',
          label: 'Extensions',
          items: [
            'semantic-ui/chart-extension',
            'semantic-ui/diagram-extension',
          ],
        },
        {
          type: 'category',
          label: 'Java / Spring Boot',
          items: [
            'semantic-ui/quickstart-spring-boot',
            'semantic-ui/server-side-rendering',
            'semantic-ui/building-an-app',
            'semantic-ui/shop-demo',
            'semantic-ui/file-explorer-demo',
          ],
        },
        {
          type: 'category',
          label: 'JavaScript client only',
          items: [
            'semantic-ui/quickstart-client',
            'semantic-ui/cdn-assets',
            'semantic-ui/ui-island',
            {type: 'link', label: 'Widget showcase ↗', href: 'pathname:///widget-demo/'},
          ],
        },
        {
          type: 'category',
          label: 'Node.js backend',
          items: [
            'semantic-ui/quickstart-node',
          ],
        },
        {
          type: 'category',
          label: 'JavaFX desktop client',
          items: [
            'semantic-ui/javafx',
          ],
        },
        {
          type: 'category',
          label: 'Tooling',
          items: [
            'semantic-ui/editor',
            {type: 'link', label: 'Visual editor ↗', href: 'pathname:///editor/'},
          ],
        },
      ],
    },
  ],
};

export default sidebars;
