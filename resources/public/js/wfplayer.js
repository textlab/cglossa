(function() {
  window.WFplayer = createReactClass({
    displayName: "WFplayer",

    componentDidMount: function() {
      var $node, corpusId, lineKey, mediaObj, start, stop;
      $node = $(ReactDOM.findDOMNode(this));
      mediaObj = this.props.mediaObj;
      corpusId = mediaObj.corpusId;
      lineKey = mediaObj.mov.lineKey;
      $("#movietitle").text(mediaObj.title);
      start = mediaObj.divs.annotation[this.props.startAt || parseInt(mediaObj.startAt)].from;
      stop = mediaObj.divs.annotation[this.props.endAt || parseInt(mediaObj.endAt)].to;
      corpusCode = mediaObj.mov.path.match(/.+\/(.+)/)[1];
      return $node.find("#waveframe").attr('src', "https://tekstlab.uio.no/wave/wfplayer-" + corpusId + "-" + mediaObj.mov.movieLoc + "-" + start + "-" + stop);
    },

    render: function() {
      return React.createElement("div", null, React.createElement("iframe", {height: "385", width: "100%", id: "waveframe", target: "_blank", className: "wfplayer"}));
    }
  });

}).call(this);
