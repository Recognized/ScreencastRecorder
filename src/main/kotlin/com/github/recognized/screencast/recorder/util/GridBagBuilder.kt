package com.github.recognized.screencast.recorder.util

import java.awt.GridBagConstraints
import java.awt.Insets


fun grid(x: Int, y: Int, fill: Int? = null, wx: Double, wy: Double): GridBagConstraints {
  return GridBagBuilder().gridx(x).gridy(y).fill(fill ?: GridBagConstraints.BOTH).weightx(wx).weighty(wy).done()
}

class GridBagBuilder {
  private val c = GridBagConstraints()

  fun done() = c

  fun gridx(value: Int): GridBagBuilder {
    c.gridx = value
    return this
  }

  fun gridy(value: Int): GridBagBuilder {
    c.gridy = value
    return this
  }

  fun fill(value: Int): GridBagBuilder {
    c.fill = value
    return this
  }

  fun anchor(value: Int): GridBagBuilder {
    c.anchor = value
    return this
  }

  fun gridheight(value: Int): GridBagBuilder {
    c.gridheight = value
    return this
  }

  fun gridwidth(value: Int): GridBagBuilder {
    c.gridwidth = value
    return this
  }

  fun insets(value: Insets): GridBagBuilder {
    c.insets = value
    return this
  }

  fun ipadx(value: Int): GridBagBuilder {
    c.ipadx = value
    return this
  }

  fun ipady(value: Int): GridBagBuilder {
    c.ipady = value
    return this
  }

  fun weightx(value: Double): GridBagBuilder {
    c.weightx = value
    return this
  }

  fun weighty(value: Double): GridBagBuilder {
    c.weighty = value
    return this
  }
}