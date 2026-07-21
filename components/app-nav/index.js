Component({
  properties: {
    current: { type: String, value: 'home' },
    items: { type: Array, value: [] },
  },

  data: {
    displayItems: [],
  },

  observers: {
    'items,current': function (items, current) {
      this.setData({
        displayItems: (items || []).map((item) => Object.assign({}, item, {
          activeClass: item.key === current ? 'is-active' : '',
        })),
      })
    },
  },

  methods: {
    handleTap(event) {
      const { key, url } = event.currentTarget.dataset
      if (!url || key === this.data.current) {
        return
      }
      wx.redirectTo({ url })
    },
  },
})
