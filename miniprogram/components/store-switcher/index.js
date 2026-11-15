Component({
  properties: {
    stores: {
      type: Array,
      value: []
    },
    value: {
      type: Number,
      value: 0
    },
    theme: {
      type: String,
      value: 'light'
    }
  },
  methods: {
    onSelect(e) {
      const id = Number(e.currentTarget.dataset.id)
      if (!id || id === this.data.value) {
        return
      }
      this.triggerEvent('change', { storeId: id })
    }
  }
})
