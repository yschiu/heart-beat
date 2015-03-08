class HeartsController < ApplicationController
  before_action :set_heart, only: [:show, :edit, :update, :destroy]
  protect_from_forgery :except => :new_from_mobile 

  # GET /hearts
  # GET /hearts.json
  def index
    @hearts = Heart.all
  end

  # GET /hearts/1
  # GET /hearts/1.json
  def show
    @song_id = get_song_id(@heart.beats)
  end

  # GET /hearts/new
  def new
    @heart = Heart.new
  end

  # GET /hearts/1/edit
  def edit
  end

  # POST /hearts
  # POST /hearts.json
  def create
    @heart = Heart.new(heart_params)

    respond_to do |format|
      if @heart.save
        format.html { redirect_to @heart, notice: 'Heart was successfully created.' }
        format.json { render :show, status: :created, location: @heart }
      else
        format.html { render :new }
        format.json { render json: @heart.errors, status: :unprocessable_entity }
      end
    end
  end

  # PATCH/PUT /hearts/1
  # PATCH/PUT /hearts/1.json
  def update
    respond_to do |format|
      if @heart.update(heart_params)
        format.html { redirect_to @heart, notice: 'Heart was successfully updated.' }
        format.json { render :show, status: :ok, location: @heart }
      else
        format.html { render :edit }
        format.json { render json: @heart.errors, status: :unprocessable_entity }
      end
    end
  end

  # DELETE /hearts/1
  # DELETE /hearts/1.json
  def destroy
    @heart.destroy
    respond_to do |format|
      format.html { redirect_to hearts_url, notice: 'Heart was successfully destroyed.' }
      format.json { head :no_content }
    end
  end

  def new_from_mobile
    @heart = Heart.new
    array = request.body.read.inspect.split(';')
    beats = array[0]
    beats[0] = ''
    signals = array[1]
    @heart.name = params[:device]
    @heart.beats = beats
    @heart.signals = signals
    @heart.duration = params[:duration]
    @heart.save

    @song_id = get_song_id(@heart.beats)
    print @song_id

    respond_to do |format|
      format.html { render :text => @song_id }
      format.any { render :text => @song_id }
    end
  end

  private
    # Use callbacks to share common setup or constraints between actions.
    def set_heart
      @heart = Heart.find(params[:id])
    end

    # Never trust parameters from the scary internet, only allow the white list through.
    def heart_params
      params.require(:heart).permit(:beat)
    end

    def get_song_id(beats)
      @song_id = "Zlot0i3Zykw"

      if beats.to_i > 70
        @song_id = "09R8_2nJtjg"
      elsif @heart.beats.to_i > 60
        @song_id = "Zlot0i3Zykw"
      elsif @heart.beats.to_i < 50
        @song_id = "qVKLNfbpCZ0"
      elsif @heart.beats.to_i < 60
        @song_id = "xWTiOqJqkk0"
      end
      return @song_id
    end
end
